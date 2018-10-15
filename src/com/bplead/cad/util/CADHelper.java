/**
 * EPMDocumentHelper.java 2018年10月13日
 */
package com.bplead.cad.util;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

import com.bplead.cad.annotation.IbaField;
import com.bplead.cad.bean.io.Attachment;
import com.bplead.cad.bean.io.AttachmentModel;
import com.bplead.cad.bean.io.CadDocument;
import com.bplead.cad.bean.io.Document;
import com.bplead.cad.config.ConfigAnalyticalTool;
import com.ptc.windchill.uwgm.common.util.PrintHelper;

import priv.lee.cad.util.Assert;
import wt.content.ApplicationData;
import wt.content.ContentHelper;
import wt.content.ContentHolder;
import wt.content.ContentRoleType;
import wt.content.ContentServerHelper;
import wt.content.DataFormatReference;
import wt.epm.EPMApplicationType;
import wt.epm.EPMAuthoringAppType;
import wt.epm.EPMContextHelper;
import wt.epm.EPMDocument;
import wt.epm.EPMDocumentType;
import wt.epm.build.EPMBuildRule;
import wt.fc.ObjectIdentifier;
import wt.fc.Persistable;
import wt.fc.PersistenceHelper;
import wt.fc.QueryResult;
import wt.fc.WTObject;
import wt.fc.collections.WTArrayList;
import wt.fc.collections.WTCollection;
import wt.folder.CabinetBased;
import wt.folder.Folder;
import wt.folder.FolderEntry;
import wt.folder.FolderHelper;
import wt.iba.value.IBAHolder;
import wt.iba.value.service.IBAValueHelper;
import wt.inf.container.WTContainer;
import wt.log4j.LogR;
import wt.method.RemoteAccess;
import wt.part.WTPart;
import wt.util.WTException;
import wt.util.WTPropertyVetoException;
import wt.vc.IterationIdentifier;
import wt.vc.VersionControlHelper;
import wt.vc.VersionIdentifier;
import wt.vc.Versioned;
import wt.vc.views.View;
import wt.vc.views.ViewHelper;
import wt.vc.wip.CheckoutLink;
import wt.vc.wip.WorkInProgressHelper;
import wt.vc.wip.Workable;

/**
 * @author zjw 2018年10月13日下午2:13:48
 */
public class CADHelper implements RemoteAccess {

    private static Logger logger = LogR.getLogger (CADHelper.class.getName ());
    
    public static EPMDocumentType componentType = EPMDocumentType.toEPMDocumentType("CADCOMPONENT");
    public static EPMAuthoringAppType authorAutoCAD = EPMAuthoringAppType.toEPMAuthoringAppType("ACAD");
    
    /**
     * Create a new EPMDocument, Specify CADName , defaultUnit and log the
     * creation to the console. If a container is given set the container for
     * the document. If an organization is given set the organization for the
     * document.
     * @throws Exception 
     */
    public static EPMDocument createEPMDocument(Document document) throws Exception {
	if (logger.isInfoEnabled ()) {
	    logger.info ("Creating document param is -> " + document);
	}

	AttachmentModel model = document.getObject ();
	CadDocument cadDoc = null;
	if (model instanceof CadDocument) {
	    cadDoc = (CadDocument) model;
	}
	Assert.isNull (cadDoc,"cadDoc is null");

	WTContainer wtcontainer = CommonUtils.getPersistable (document.getContainer ().getContainer ().getOid (),
		WTContainer.class);
	Assert.isNull (wtcontainer,"wtcontainer is null");

	Folder folder = CommonUtils.getPersistable (document.getContainer ().getFolder ().getOid (),Folder.class);
	Assert.isNull (folder,"folder is null");
	
	try {
	    EPMContextHelper.setApplication(EPMApplicationType.getEPMApplicationTypeDefault());
	}
	catch(WTPropertyVetoException e1) {
	    e1.printStackTrace();
	}
	//get cadName
	List<Attachment> attachments = cadDoc.getAttachments ();
	File shareDirectory = CommonUtils.getShareDirectory ();
	String cadName = "";
	for (Attachment attachment : attachments) {
	    File file = new File (shareDirectory,attachment.getName ());
	    Assert.isTrue (file.exists (),"File[" + file.getPath () + "] does not exist");
	    Assert.isTrue (file.isFile (),"File[" + file.getPath () + "] is not a file");
	    if (attachment.isPrimary ()) {
		cadName = attachment.getName ();
		break;
	    }
	}
	
	EPMDocument epmDoc =  EPMDocument.newEPMDocument (cadDoc.getNumber (),cadDoc.getName (),authorAutoCAD,componentType,cadName);
	// If the folder is null, put the document in the container's default
	// cabinet.
	if (folder == null && wtcontainer != null) {
	    folder = wtcontainer.getDefaultCabinet ();
	}

	FolderHelper.assignLocation ((FolderEntry) epmDoc,folder);

	try {
	    if (wtcontainer != null) {
		epmDoc.setContainer (wtcontainer);
	    }
	}
	catch(WTPropertyVetoException e) {
	    throw new WTException (e);
	}
	epmDoc = (EPMDocument) PersistenceHelper.manager.save (epmDoc);
	if (logger.isInfoEnabled ()) {
	    logger.info ("create document success !");
	}
	//process iba attribute TODO
	epmDoc = processIBAHolder (epmDoc,cadDoc,EPMDocument.class);
	
	//upload epmdoc content
	epmDoc = (EPMDocument) saveContents (epmDoc,cadDoc.getAttachments ());
	
	return CommonUtils.refresh(epmDoc, EPMDocument.class);
    }
    
    public static EPMDocument updateEPMDocument(Document document) throws Exception {
	if (logger.isInfoEnabled ()) {
	    logger.info ("Update document param is -> " + document);
	}
	AttachmentModel model = document.getObject ();
	CadDocument cadDoc = null;
	if (model instanceof CadDocument) {
	    cadDoc = (CadDocument) model;
	}
	Assert.isNull (cadDoc,"cadDoc is null");
	
	EPMDocument epmDoc = CommonUtils.getPersistable (document.getOid (),EPMDocument.class);
	Assert.isNull (epmDoc,"epmdocument is null.");
	EPMDocument workingCopy = null;
	if (WorkInProgressHelper.isCheckedOut (epmDoc)) {
	    workingCopy = (EPMDocument) WorkInProgressHelper.service.workingCopyOf (epmDoc);
	}
	Assert.isNull (workingCopy,"epmdocument is not checkout.");

	//process iba attribute TODO 
	workingCopy = processIBAHolder (workingCopy,cadDoc,EPMDocument.class);
	
	//upload epmdoc content
	workingCopy = (EPMDocument) saveContents (workingCopy,cadDoc.getAttachments ());
	
	return CommonUtils.checkin (workingCopy,"cad toll update EPMDocument.",EPMDocument.class);
    }
    
    /**
     * Create a Document, its corresponding Part, and establish a build rule
     * between them
     * @throws Exception 
     */
    @SuppressWarnings("deprecation")
    public static WTObject [] saveDocAndPart(Document document) throws Exception {
//	Assert.isTrue(AccessControlHelper.manager..hasAccess(document, AccessPermission.CREATE), CommonUtils
//		.toLocalizedMessage(CustomPrompt.ACCESS_DENIED, document.getNumber(), AccessPermission.CREATE));
	//oid exist
	EPMDocument epm = null;
	if (StringUtils.isNotBlank(document.getOid())) {
	    epm = updateEPMDocument(document);
	} else {
	    epm = createEPMDocument(document);
	}
	// related wtpart
	WTPart part = null;
	if (document.getRelatedPart ()) {
	    String classConfig = ConfigAnalyticalTool.getPropertiesValue ("find_associated_part_classname");
	    if (logger.isDebugEnabled ()) {
		logger.debug ("find_associated_part_classname is -> " + classConfig);
	    }
	    if (StringUtils.isEmpty (classConfig)) {
		DefaultFindAssociatePart find = new DefaultFindAssociatePart ();
		part = find.getAssociatePart (document);
	    } else {
		FindAssociatePart find = (FindAssociatePart) Class.forName (classConfig).newInstance ();
		part = find.getAssociatePart (document);
	    }
	    Assert.isNull (part,"releated part is null");
	}
	
	// First, check out the part if it is in a shared folder.
	if (!FolderHelper.inPersonalCabinet ((CabinetBased) part)) {
	    part = (WTPart) checkout (part,"Part");
	}

	// do EPMBuildRule
	EPMBuildRule rule = null;
	if (document.getBuildType () == 0) {
	    rule = createBuildRule (epm,part);
	} else {
	    rule = createBuildRule (epm,part,document.getBuildType ());
	}

	// Check in the part if we checked it out in order to create the build
	// rule.
	if (WorkInProgressHelper.isCheckedOut (part)) {
	    part = (WTPart) checkin (part,"Part");

	    /*
	     * An EPM build rule is a version to version link. When you check
	     * out a part, the system creates a new iteration of the original
	     * called the working copy. The working copy belongs a different
	     * version than its original. Since the part is checked out, this
	     * method creates a build rule to the temporary version created for
	     * the working copy. During check in, a listener moves the build
	     * rule to the original version. Refresh the build rule, since it
	     * was modified during the check in.
	     */
	    rule = (EPMBuildRule) PersistenceHelper.manager.refresh (rule);
	}

	WTObject [] objects = new WTObject [3];
	objects[0] = epm;
	objects[1] = part;
	objects[2] = rule;

	return objects;
    }

    public static EPMBuildRule createBuildRule(EPMDocument source, WTPart target) throws WTException {
	return createBuildRule (source,target,EPMBuildRule.ALL_BUILD_ROLES);
    }

    public static EPMBuildRule createBuildRule(EPMDocument source, WTPart target, int buildType) throws WTException {
	if (logger.isInfoEnabled ()) {
	    logger.info ("Creating build rule " + PrintHelper.printPersistable (source) + " "
		    + PrintHelper.printPersistable (target));
	}
	EPMBuildRule rule = EPMBuildRule.newEPMBuildRule (source,target,buildType);
	rule = (EPMBuildRule) PersistenceHelper.manager.save (rule);
	if (logger.isInfoEnabled ()) {
	    logger.info ("Creating build rule success !");
	}
	return rule;
    }


    public static WTPart createPart(Document document) throws WTException {
	if (logger.isInfoEnabled ()) {
	    logger.info ("Creating part params is -> " + document);
	}

	AttachmentModel model = document.getObject ();
	CadDocument cadDoc = null;
	if (model instanceof CadDocument) {
	    cadDoc = (CadDocument) model;
	}
	Assert.isNull (cadDoc,"cadDoc is null");

	WTContainer wtcontainer = CommonUtils.getPersistable (document.getContainer ().getContainer ().getOid (),
		WTContainer.class);
	Assert.isNull (wtcontainer,"wtcontainer is null");

	Folder folder = CommonUtils.getPersistable (document.getContainer ().getFolder ().getOid (),Folder.class);
	Assert.isNull (folder,"folder is null");

	WTPart part = WTPart.newWTPart (removeExtension (cadDoc.getNumber ()),cadDoc.getName ());
	View view = ViewHelper.service.getView ("Design");
	if (view != null) {
	    ViewHelper.assignToView (part,view);
	}

	// If the folder is null, put the part in the container's default
	// cabinet.
	if (folder == null && wtcontainer != null) {
	    folder = wtcontainer.getDefaultCabinet ();
	}
	// set folder
	FolderHelper.assignLocation ((FolderEntry) part,folder);

	try {
	    if (wtcontainer != null) {
		part.setContainer (wtcontainer);
	    }
	}
	catch(WTPropertyVetoException e) {
	    throw new WTException (e);
	}

	part = (WTPart) PersistenceHelper.manager.save (part);

	if (logger.isInfoEnabled ()) {
	    logger.info ("Creating part success !");
	}
	return part;
    }

    public static Workable checkin(Workable object, String note) throws WTException, WTPropertyVetoException {
	if (logger.isInfoEnabled ()) {
	    logger.info ("Checking in " + PrintHelper.printIterated (object));
	}
	boolean refreshMe = false;
	if (object instanceof IBAHolder) {
	    refreshMe = ( (IBAHolder) object ).getAttributeContainer () != null;
	}
	object = WorkInProgressHelper.service.checkin (object,note);
	// AttributeContainers are stripped off by checkin, so they must be
	// refreshed
	if (refreshMe) {
	    object = (Workable) readValues ((IBAHolder) object,null);
	}
	if (logger.isInfoEnabled ()) {
	    logger.info ("Checking in success !");
	}
	return object;
    }

    public static IBAHolder readValues(IBAHolder holder, Object constraintParameter) throws WTException {
	try {
	    holder = IBAValueHelper.service.refreshAttributeContainer (holder,constraintParameter,null,null);
	}
	catch(java.rmi.RemoteException e) {
	    throw new WTException (e);
	}
	return holder;
    }

    public static Workable checkout(Workable object, String note) throws WTException, WTPropertyVetoException {
	if (logger.isInfoEnabled ()) {
	    logger.info ("Checking out " + note + " " + PrintHelper.printIterated (object));
	}
	Folder checkoutFolder = WorkInProgressHelper.service.getCheckoutFolder ();
	CheckoutLink checkOutLink = WorkInProgressHelper.service.checkout (object,checkoutFolder, note);
	Workable workingCopy = checkOutLink.getWorkingCopy ();
	if (logger.isInfoEnabled ()) {
	    logger.info ("Checking out success !");
	}
	return workingCopy;
    }
    
    public static Workable checkout(Workable object, String note, Folder checkoutFolder)
	    throws WTException, WTPropertyVetoException {
	if (logger.isInfoEnabled ()) {
	    logger.info ("Checking out " + note + " " + PrintHelper.printIterated (object));
	}
	CheckoutLink checkOutLink = WorkInProgressHelper.service.checkout (object,checkoutFolder,note);
	Workable workingCopy = checkOutLink.getWorkingCopy ();
	if (logger.isInfoEnabled ()) {
	    logger.info ("Checking out success !");
	}
	return workingCopy;
    }
    
    public static Workable [] checkout(Workable [] objects, String note) throws WTException, WTPropertyVetoException {
	return checkout (objects,note,WorkInProgressHelper.service.getCheckoutFolder ());
    }
	 
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static Workable [] checkout(Workable [] objects, String note, Folder checkoutFolder)
	    throws WTException, WTPropertyVetoException {
	if (logger.isInfoEnabled ()) {
	    logger.info ("Checking out " + objects.length + " note is -> " + note);
	}
	String [] notes = new String [objects.length];
	HashMap originals = new HashMap ();
	for (int i = 0; i < objects.length; i++) {
	    notes[i] = "test";
	    String name = getID ((Versioned) objects[i]);
	    originals.put (name,new Long (i));
	}

	// Currently "notes" are ignored
	WTArrayList checkoutObjs = new WTArrayList (Arrays.asList (objects));
	WTCollection checkOutLinks = checkout (checkoutObjs,checkoutFolder,note);

	Workable [] checkedoutCopies = new Workable [objects.length];
	Iterator i_checkedOut = checkOutLinks.persistableIterator ();
	while (i_checkedOut.hasNext ()) {
	    Workable wCopy = ( (CheckoutLink) i_checkedOut.next () ).getWorkingCopy ();
	    Long longIndex = (Long) originals.get (getID ((Versioned) wCopy));
	    int k = longIndex.intValue ();
	    checkedoutCopies[k] = wCopy;
	}
	if (logger.isInfoEnabled ()) {
	    logger.info ("Checking out success !");
	}
	return checkedoutCopies;
    }
    
    public static WTCollection checkout(WTCollection objects, Folder checkoutFolder, String note)
	    throws WTException, WTPropertyVetoException {
	WTCollection checkOutLinks = WorkInProgressHelper.service.checkout (objects,checkoutFolder,note);
	return checkOutLinks;
    }
    
    public static Workable undoCheckout(Workable object) throws WTException, WTPropertyVetoException {
	if (logger.isInfoEnabled ()) {
	    logger.info ("Undo Checkout of " + PrintHelper.printIterated (object));
	}
	Workable originalCopy = WorkInProgressHelper.service.undoCheckout (object);
	if (logger.isInfoEnabled ()) {
	    logger.info ("Undo Checkout success !");
	}
	return originalCopy;
    }
    
    public static Persistable [] undoCheckout(Persistable [] objects)
	    throws WTException, WTPropertyVetoException {
	return undoCheckout (objects,true);
    }

    public static Persistable [] undoCheckout(Persistable [] objects, boolean filterOriginals)
	    throws WTException, WTPropertyVetoException {
	if (logger.isInfoEnabled ()) {
	    logger.info ("Undo Checkout of " + objects.length + " filterOriginals is -> " + filterOriginals);
	}
	WTArrayList toUndo = new WTArrayList ();
	if (filterOriginals == true) {
	    boolean [] isUndo = new boolean [objects.length];
	    for (int i = 0; i < objects.length; i++) {
		if (WorkInProgressHelper.isCheckedOut ((Workable) objects[i]) == true) {
		    isUndo[i] = true;
		    toUndo.addElement (objects[i]);
		} else
		    isUndo[i] = false;
	    }
	    if (toUndo.size () > 0) {
		Persistable [] temp = new Persistable [toUndo.size ()];
		WTCollection undone = undoCheckouts (toUndo);
		temp = undone.toArray (temp);
		int iUndo = 0;
		for (int i = 0; i < isUndo.length; i++) {
		    if (isUndo[i] == true) objects[i] = temp[iUndo++];
		}
	    }
	} else {
	    for (int i = 0; i < objects.length; i++) {
		toUndo.addElement (objects[i]);
	    }
	    WTCollection undone = undoCheckouts (toUndo);
	    objects = undone.toArray (objects);
	}
	if (logger.isInfoEnabled ()) {
	    logger.info ("Undo Checkout success !");
	}
	return objects;
    }

    public static WTCollection undoCheckouts(WTCollection objects) throws WTException, WTPropertyVetoException {
	WTCollection undone = WorkInProgressHelper.service.undoCheckouts (objects);
	return undone;
    }
    
    public static HashMap<String,String> downLoad (ContentHolder contentHolder,String targetPath,String roleType) {
        HashMap<String,String> contentMap = new HashMap<String, String>();
        if (contentHolder == null)
            return contentMap;
        logger.debug ("downLoad param contentHolder is -> " + contentHolder + " roleType is -> " + roleType);
        File file = new File(targetPath);
        if (!file.exists()) {
            file.mkdirs();
        }
        try {
            if (StringUtils.equals(roleType, "0") || StringUtils.equals(roleType, "2")) {
                QueryResult qrPrimary = wt.content.ContentHelper.service.getContentsByRole(contentHolder, ContentRoleType.PRIMARY);
                logger.debug ("qrPrimary qr size is -> " + qrPrimary.size());
                while (qrPrimary.hasMoreElements()) {
                    ApplicationData primaryData = (ApplicationData) qrPrimary.nextElement();
                    String primaryFile = primaryData.getFileName();
                    if (StringUtils.equals(primaryFile, "{$CAD_NAME}")) {
                        primaryFile = ((EPMDocument)contentHolder).getCADName();
                    }
                    logger.debug ("primaryData name is -> " + primaryFile + " format data name is -> " + primaryData.getFormat().getDataFormat().getFormatName());
                    String primaryPath = targetPath + File.separator + primaryFile;
                    logger.debug ("primaryPath is -> " + primaryPath);
                    wt.content.ContentServerHelper.service.writeContentStream(primaryData,primaryPath);
                    
                    contentMap.put(primaryFile, primaryPath);
                }
            }
            if (StringUtils.equals(roleType, "1") || StringUtils.equals(roleType, "2")) {
                QueryResult qrSecondary = wt.content.ContentHelper.service.getContentsByRole(contentHolder, ContentRoleType.SECONDARY);
                logger.debug ("qrSecondary qr size is -> " + qrSecondary.size());
                while (qrSecondary.hasMoreElements()) {
                    ApplicationData secondaryData = (ApplicationData) qrSecondary.nextElement();
                    String secondaryFile = secondaryData.getFileName();
                    logger.debug ("secondaryData name is -> " + secondaryFile + " format data name is -> " + secondaryData.getFormat().getDataFormat().getFormatName());
                    String secondaryPath = targetPath + File.separator + secondaryFile;
                    logger.debug ("secondaryPath is -> " + secondaryPath);
                    wt.content.ContentServerHelper.service.writeContentStream(secondaryData,secondaryPath);
                    
                    contentMap.put(secondaryFile, secondaryPath);
                }
            }
        } catch (WTException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return contentMap;
    }
    
    public static <T extends IBAHolder> T processIBAHolder (IBAHolder ibaHolder,CadDocument cadDoc,Class<T> clazz) throws Exception {
	IBAUtils ibaTool = new IBAUtils (ibaHolder);
	setIBAValues (ibaTool,cadDoc,clazz);
	return (T)ibaTool.updateAttributeContainer (ibaHolder,clazz);
    }
    
    public static void setIBAValues(IBAUtils ibaTool, CadDocument cadDoc, Class<?> clazz) {
	Field [] fields = cadDoc.getClass ().getDeclaredFields ();
	String ibaTarget = clazz.getSimpleName ();
	for (Field field : fields) {
	    try {
		field.setAccessible (true);
		//value is null,continue
		Object object = field.get (cadDoc);
		if (object == null) {
		    continue;
		}
		IbaField ibaField = field.getAnnotation (IbaField.class);
		//target() contains ibaTarget
		if (StringUtils.contains (ibaField.target (),ibaTarget)) {
		    ibaTool.setIBAValue (ibaField.ibaName (),object.toString ());
		    if (logger.isDebugEnabled ()) {
			logger.debug ("setIBAValues iba name is -> " + ibaField.ibaName () + " iba value is -> " + object);
		    }
		}
	    }
	    catch(Exception e) {
		e.printStackTrace ();
	    }
	}
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static ContentHolder saveContents (ContentHolder holder,List<Attachment> attachments) throws IOException, WTException {
	File shareDirectory = CommonUtils.getShareDirectory ();
	HashMap contentMap = new HashMap();
	HashMap secondaryMap = null;
	for (Attachment attachment : attachments) {
	    File file = new File (shareDirectory,attachment.getName ());
	    Assert.isTrue (file.exists (),"File[" + file.getPath () + "] does not exist");
	    Assert.isTrue (file.isFile (),"File[" + file.getPath () + "] is not a file");
	    if (attachment.isPrimary ()) {
                String[] primaryPaths = new String[] {attachment.getAbsolutePath (),"cad tool upload primary."};
                contentMap.put ("primary",primaryPaths);
	    } else {
		if (secondaryMap == null) {
		    secondaryMap = new HashMap();
		}
                String[] secondaryPaths = new String[] {attachment.getAbsolutePath (),"cad tool upload secondary."};
                secondaryMap.put (attachment.getName (),secondaryPaths);
	    }
	}
	if (secondaryMap != null) {
	    contentMap.put ("secondary",secondaryMap);
	}
	
	upload (holder,contentMap);
	
	return holder;
    }

    public static String upload (ContentHolder contentHolder,HashMap<String,?> targetPathMap) throws WTException {
        StringBuffer errorSB = new StringBuffer();
        logger.debug("upload param is -> contentHolder=[" + contentHolder + "] targetPathMap=" + targetPathMap);
        if (contentHolder == null || targetPathMap.isEmpty()) {
            errorSB.append("param is null ...... ");
        }
        try {
            if (targetPathMap.containsKey("primary")) {
                String[] primaryPaths = (String[]) targetPathMap.get("primary");
                String primaryPath = primaryPaths.length > 0 ? primaryPaths[0] : "";
                String primaryDescription = primaryPaths.length > 1 ? primaryPaths[1] : "";
                File uploadFile = new File(primaryPath);
                if (uploadFile.exists() && uploadFile.isFile()) {
                    QueryResult qrPrimary = wt.content.ContentHelper.service.getContentsByRole(contentHolder, ContentRoleType.PRIMARY);
                    while (qrPrimary.hasMoreElements()) {
                        ApplicationData primaryData = (ApplicationData) qrPrimary.nextElement();
                        ContentServerHelper.service.deleteContent(contentHolder, primaryData);
                    }
                    ApplicationData data = ApplicationData.newApplicationData(contentHolder);
                    data.setFileName(uploadFile.getName());
                    data.setFileSize(uploadFile.length());
                    data.setUploadedFromPath(primaryPath);
                    data.setRole(ContentRoleType.PRIMARY);
                    data.setDescription(primaryDescription);
                    if (primaryPath.endsWith(".xls") || primaryPath.endsWith(".xlsx") || primaryPath.endsWith(".XLS") || primaryPath.endsWith(".XLSX")) {
                        data.setFormat(DataFormatReference.newDataFormatReference(ContentHelper.service.getFormatByName("Microsoft Excel")));
                    } else if (primaryPath.endsWith(".doc") || primaryPath.endsWith(".docx") || primaryPath.endsWith(".DOC") || primaryPath.endsWith(".DOCX")) {
                        data.setFormat(DataFormatReference.newDataFormatReference(ContentHelper.service.getFormatByName("Microsoft Word")));                    
                    } else if (primaryPath.endsWith(".pdf") || primaryPath.endsWith(".PDF")) {
                        data.setFormat(DataFormatReference.newDataFormatReference(ContentHelper.service.getFormatByName("PDF")));
                    } else if (primaryPath.endsWith(".dwg") || primaryPath.endsWith(".DWG")) {
                        data.setFormat(DataFormatReference.newDataFormatReference(ContentHelper.service.getFormatByName("DWG")));
                    } 
                    ContentServerHelper.service.updateContent(contentHolder, data, primaryPath);
                    //上传完成后删除
                    uploadFile.deleteOnExit();
                } else {
                    errorSB.append("new file=[" + primaryPath + "] is not exist ...");
                }
            }
            
            if (targetPathMap.containsKey("secondary")) {
                HashMap<?,?> attachmentsMap = (HashMap<?, ?>) targetPathMap.get("secondary");
                if (attachmentsMap != null && !attachmentsMap.isEmpty()) {
                    QueryResult qrSecondary = wt.content.ContentHelper.service.getContentsByRole(contentHolder, ContentRoleType.SECONDARY);
                    while (qrSecondary.hasMoreElements()) {
                        ApplicationData secondaryData = (ApplicationData) qrSecondary.nextElement();
                        String secondaryFile = secondaryData.getFileName();
                        logger.debug("secondaryFile is -> " + secondaryFile);
                        if (attachmentsMap.containsKey(secondaryFile)) {
                            ContentServerHelper.service.deleteContent(contentHolder, secondaryData);
                        }
                    }
                    
                    Iterator<?> iter = attachmentsMap.keySet().iterator();
                    while (iter.hasNext()) {
                        String fileName = (String) iter.next();
                        String[] secondaryPaths = (String[]) attachmentsMap.get(fileName);
                        String secondaryPath = secondaryPaths.length > 0 ? secondaryPaths[0] : "";
                        String secondaryDescription = secondaryPaths.length > 1 ? secondaryPaths[1] : "";
                        File uploadFile = new File(secondaryPath);
                        if (uploadFile.exists() && uploadFile.isFile()) {
                            ApplicationData data = ApplicationData.newApplicationData(contentHolder);
                            data.setFileName(uploadFile.getName());
                            data.setFileSize(uploadFile.length());
                            data.setUploadedFromPath(secondaryPath);
                            data.setRole(ContentRoleType.SECONDARY);
                            data.setDescription(secondaryDescription);
                            if (secondaryPath.endsWith(".xls") || secondaryPath.endsWith(".xlsx") || secondaryPath.endsWith(".XLS") || secondaryPath.endsWith(".XLSX")) {
                                data.setFormat(DataFormatReference.newDataFormatReference(ContentHelper.service.getFormatByName("Microsoft Excel")));
                            } else if (secondaryPath.endsWith(".doc") || secondaryPath.endsWith(".docx") || secondaryPath.endsWith(".DOC") || secondaryPath.endsWith(".DOCX")) {
                                data.setFormat(DataFormatReference.newDataFormatReference(ContentHelper.service.getFormatByName("Microsoft Word")));                            
                            } else if (secondaryPath.endsWith(".pdf") || secondaryPath.endsWith(".PDF")) {
                                data.setFormat(DataFormatReference.newDataFormatReference(ContentHelper.service.getFormatByName("PDF")));
                            } else if (secondaryPath.endsWith(".dwg") || secondaryPath.endsWith(".DWG")) {
                                data.setFormat(DataFormatReference.newDataFormatReference(ContentHelper.service.getFormatByName("DWG")));
                            } 
                            ContentServerHelper.service.updateContent(contentHolder, data, secondaryPath);
                            //上传完成后删除
                            uploadFile.deleteOnExit();
                        } else {
                            errorSB.append("new file=[" + secondaryPath + "] is not exist ...");
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof WTException) {
                throw (WTException)e;
            } else {
                throw new WTException(e.getLocalizedMessage());
            }
        } 
        
        return errorSB.toString();
    }
    
    public static String getID(Versioned object) throws WTException, WTPropertyVetoException {
	VersionIdentifier versionID = VersionControlHelper.getVersionIdentifier (object);
	if (versionID == null) versionID = VersionIdentifier.newVersionIdentifier ();

	IterationIdentifier iterationID = VersionControlHelper.getIterationIdentifier (object);
	if (iterationID == null) iterationID = IterationIdentifier.newIterationIdentifier ();

	StringBuffer versionLabel = new StringBuffer (versionID.getValue ());
	versionLabel.append ('.');
	versionLabel.append (iterationID.getValue ());

	return getOID (object.getMaster ()) + versionLabel.toString ();
    }
    
    static long getOID(Persistable p) {
        ObjectIdentifier oid = p.getPersistInfo().getObjectIdentifier();
        return oid == null ? 0 : oid.getId();
    }
    
    /**
     * Get base filename from a full path filename.
     *
     * <pre>
     *   Example Usage:
     *    FileUtil.getBaseFilename( "C:\myFolder\myFile.txt" ); //returns "myFile.txt"
     *
     *  </pre>
     *
     * <BR><BR><B>Supported API: </B>false
     **/
    public static String getBaseFilename(String fullname) {
	int start;
	start = fullname.lastIndexOf ("/");
	if (start == -1) {
	    start = fullname.lastIndexOf ("\\");
	}
	if (start == -1) {
	    return fullname;
	}

	return fullname.substring (start + 1);
    }
    
    /**
     * Remove extension from a filename.
     *
     * <pre>
     *   Example Usage:
     *    FileUtil.removeExtension( "C:\myFile.txt" )  // returns "C:\myFile"
     *
     *  </pre>
     * <BR><BR><B>Supported API: </B>false
     *
     * @param   String - name of file
     *
     * @return  filename with extension removed
     **/
    public static String removeExtension(String filename) { // NOTE: could call WTStringUtilites.trimTail()
	for (int i = filename.length () - 1; i >= 0; i--) {
	    if (filename.charAt (i) == '.') {
		String file = filename.substring (0,i);
		return file;
	    }
	}
	return filename;
    }
 
    /**
     * Get extension from a filename.
     *
     * <pre>
     *   Example Usage:
     *    FileUtil.removeExtension( "C:\myFile.txt" )  // returns ".txt"
     *
     *  </pre>
     * @param   String - name of file
     *
     * @return  extension of filename
     **/
    public static String getExtension(String filename) { // NOTE: could call WTStringUtilites.tail()
	for (int i = filename.length () - 1; i >= 0; i--) {
	    if (filename.charAt (i) == '.') {
		String ext = filename.substring (i + 1,filename.length ());
		return ext;
	    }
	}
	return new String ("");
    }
 
    /**
     * Set extension for a filename.
     *
     * <pre>
     *   Example Usage:
     *    FileUtil.removeExtension( "C:\myFile", "txt" )  // returns "C:\myFile.txt"
     *
     *  </pre>
     * <BR><BR><B>Supported API: </B>false
     *
     * @param   filename   name of file
     * @param   extension  extension to add to file
     *
     * @return  filename with extension added
     **/
    public static String setExtension(String filename, String extension) {
	String currentExtension = getExtension (filename);
	String newFileName;
	if (currentExtension.equals ("")) {
	    newFileName = filename + "." + extension;
	} else {
	    if (extension.equals ("")) {
		newFileName = filename.substring (0,filename.length () - currentExtension.length () - 1);
	    } else {
		newFileName = filename.substring (0,filename.length () - currentExtension.length ()) + extension;
	    }
	}

	return newFileName;
    }
    
    public static EPMDocumentType getEPMDocumentType(String name) {
	String ext = getExtension (name);
	if (ext == null) {
	    return EPMDocumentType.getEPMDocumentTypeDefault ();
	}

	if (ext.equalsIgnoreCase ("asm")) {
	    return EPMDocumentType.toEPMDocumentType ("CADASSEMBLY");
	} else if (ext.equalsIgnoreCase ("prt")) {
	    return EPMDocumentType.toEPMDocumentType ("CADCOMPONENT");
	} else if (ext.equalsIgnoreCase ("drw")) {
	    return EPMDocumentType.toEPMDocumentType ("CADDRAWING");
	} else {
	    return EPMDocumentType.getEPMDocumentTypeDefault ();
	}
    }
 
    
}
