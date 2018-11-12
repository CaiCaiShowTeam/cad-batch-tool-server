/**
 * EPMDocumentHelper.java 2018年10月13日
 */
package com.bplead.cad.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import org.apache.log4j.Logger;

import com.bplead.cad.annotation.IbaField;
import com.bplead.cad.bean.SimpleDocument;
import com.bplead.cad.bean.SimpleFolder;
import com.bplead.cad.bean.SimplePdmLinkProduct;
import com.bplead.cad.bean.io.Attachment;
import com.bplead.cad.bean.io.AttachmentModel;
import com.bplead.cad.bean.io.CadDocument;
import com.bplead.cad.bean.io.CadStatus;
import com.bplead.cad.bean.io.Container;
import com.bplead.cad.bean.io.Document;
import com.bplead.cad.bean.io.PartCategory;
import com.bplead.cad.config.ConfigAnalyticalTool;
import com.ptc.windchill.cadx.common.WTPartUtilities;
import com.ptc.windchill.uwgm.common.util.PrintHelper;

import priv.lee.cad.util.Assert;
import priv.lee.cad.util.StringUtils;
import wt.build.BuildSource;
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
import wt.folder.Folder;
import wt.folder.FolderEntry;
import wt.folder.FolderException;
import wt.folder.FolderHelper;
import wt.folder.FolderNotFoundException;
import wt.folder.Foldered;
import wt.iba.value.IBAHolder;
import wt.iba.value.service.IBAValueHelper;
import wt.inf.container.WTContained;
import wt.inf.container.WTContainer;
import wt.inf.container.WTContainerRef;
import wt.lifecycle.State;
import wt.log4j.LogR;
import wt.method.RemoteAccess;
import wt.part.Quantity;
import wt.part.WTPart;
import wt.part.WTPartMaster;
import wt.part.WTPartStandardConfigSpec;
import wt.part.WTPartUsageLink;
import wt.pds.StatementSpec;
import wt.query.QuerySpec;
import wt.query.SearchCondition;
import wt.util.WTAttributeNameIfc;
import wt.util.WTException;
import wt.util.WTPropertyVetoException;
import wt.vc.Iterated;
import wt.vc.IterationIdentifier;
import wt.vc.VersionControlHelper;
import wt.vc.VersionForeignKey;
import wt.vc.VersionIdentifier;
import wt.vc.VersionReference;
import wt.vc.VersionToVersionLink;
import wt.vc.Versioned;
import wt.vc.config.ConfigSpec;
import wt.vc.config.LatestConfigSpec;
import wt.vc.struct.StructHelper;
import wt.vc.views.View;
import wt.vc.views.ViewHelper;
import wt.vc.wip.CheckoutLink;
import wt.vc.wip.WorkInProgressHelper;
import wt.vc.wip.Workable;

/**
 * 2018年10月13日下午2:13:48
 */
public class CADHelper implements RemoteAccess {

    public static EPMAuthoringAppType authorAutoCAD = EPMAuthoringAppType.toEPMAuthoringAppType ("ACAD");

    public static EPMDocumentType componentType = EPMDocumentType.toEPMDocumentType ("CADCOMPONENT");

    private static final String DEFAULT_FOLDER = "/Default";
    private static Logger logger = LogR.getLogger (CADHelper.class.getName ());

    public static SimpleFolder buildSimpleFolder(Foldered foldered) throws WTException {
	SimpleFolder folder = new SimpleFolder ();
	folder.setOid (CommonUtils.getPersistableOid (getParentFolder (foldered)));
	folder.setName (getFolderPath (foldered));
	folder.setSelected (true);
	return folder;
    }

    public static SimplePdmLinkProduct buildSimpleProduct(WTContained wtcontained) {
	SimplePdmLinkProduct product = new SimplePdmLinkProduct ();
	product.setOid (CommonUtils.getPersistableOid (wtcontained.getContainer ()));
	product.setName (wtcontained.getContainerName ());
	product.setSelected (true);
	return product;
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

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static Workable [] checkin(Workable [] objects, String note) throws WTException, WTPropertyVetoException {
	int num_objects = objects.length;
	if (logger.isDebugEnabled ()) {
	    logger.debug ("Multiple Checkin of " + num_objects + " objects " + note);
	}
	String [] notes = new String [num_objects];
	boolean [] refreshMe = new boolean [num_objects];
	HashMap ordering = new HashMap ();
	for (int j = 0; j < num_objects; j++) {
	    notes[j] = note;
	    refreshMe[j] = ( objects[j] instanceof IBAHolder
		    && ( (IBAHolder) objects[j] ).getAttributeContainer () != null );
	    ordering.put (new Long (getOID (objects[j])),new Long (j));
	}
	WTArrayList checkinObjs = new WTArrayList (Arrays.asList (objects));
	WTCollection checkedIn = checkin (checkinObjs,"test");

	// AttributeContainers are stripped off by checkin, they must be
	// refreshed
	Workable [] newIterations = new Workable [objects.length];
	Iterator i_checkedIn = checkedIn.persistableIterator ();
	while (i_checkedIn.hasNext ()) {
	    Persistable checkInObject = (Persistable) i_checkedIn.next ();
	    Long longIndex = (Long) ordering.get (new Long (getOID (checkInObject)));
	    int k = longIndex.intValue ();
	    newIterations[k] = (Workable) checkInObject;
	    if (refreshMe[k]) newIterations[k] = (Workable) readValues ((IBAHolder) checkInObject,null);
	}
	return newIterations;
    }

    // returns a WTCollection of CheckoutLink's
    public static WTCollection checkin(WTCollection objects, String note) throws WTException, WTPropertyVetoException {
	WTCollection checkedIn = WorkInProgressHelper.service.checkin (objects,note);
	return checkedIn;
    }

    public static SimpleDocument checkout(Document document) throws WTException, WTPropertyVetoException {
	if (logger.isInfoEnabled ()) {
	    logger.info ("checkout of " + document);
	}
	EPMDocument epm = getDocumentByNumber (document.getNumber ());

	EPMDocument workingCopy = (EPMDocument) checkout (epm,"cad tool checkout");

	if (logger.isInfoEnabled ()) {
	    logger.info ("checkout success !");
	}
	SimpleDocument simpleDocument = new SimpleDocument (CommonUtils.getPersistableOid (workingCopy),
		workingCopy.getName (),workingCopy.getNumber ());
	simpleDocument.setCadStatus (CadStatus.CHECK_OUT);

	return simpleDocument;
    }

    public static Workable checkout(Workable object, String note) throws WTException, WTPropertyVetoException {
	if (logger.isInfoEnabled ()) {
	    logger.info ("Checking out " + note + " " + PrintHelper.printIterated (object));
	}
	Folder checkoutFolder = WorkInProgressHelper.service.getCheckoutFolder ();
	CheckoutLink checkOutLink = WorkInProgressHelper.service.checkout (object,checkoutFolder,note);
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

    /**
     * Create a new EPMDocument, Specify CADName , defaultUnit and log the
     * creation to the console. If a container is given set the container for
     * the document. If an organization is given set the organization for the
     * document.
     * 
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
	Assert.notNull (cadDoc,"cadDoc is null");
	if (logger.isInfoEnabled ()) {
	    logger.info ("创建epm文档获取容器和文件夹开始... ");
	}
	WTContainer wtcontainer = getWTContainerByName (document.getContainer ().getProduct ().getName ());
	Assert.notNull (wtcontainer,"wtcontainer is null");

	Folder folder = getFolder (document.getContainer ().getFolder ().getName (),wtcontainer);
	Assert.notNull (folder,"folder is null");
	if (logger.isInfoEnabled ()) {
	    logger.info ("创建epm文档获取容器和文件夹结束... ");
	    logger.debug ("wtcontainer is -> " + PrintHelper.printContainer (wtcontainer) + " folder is -> "
		    + PrintHelper.printFolder (folder));
	}
	try {
	    EPMContextHelper.setApplication (EPMApplicationType.getEPMApplicationTypeDefault ());
	}
	catch(WTPropertyVetoException e1) {
	    e1.printStackTrace ();
	}
	// get cadName
	List<Attachment> attachments = cadDoc.getAttachments ();
	File shareDirectory = CommonUtils.getShareDirectory ();
	String cadName = "";
	for (Attachment attachment : attachments) {
	    File file = new File (shareDirectory,attachment.getName ());
	    Assert.isTrue (file.exists (),"File[" + file.getPath () + "] does not exist");
	    Assert.isTrue (file.isFile (),"File[" + file.getPath () + "] is not a file");
	    if (attachment.isPrimary ()) {
		cadName = attachment.getRealName ();
		break;
	    }
	}

	EPMDocument epmDoc = EPMDocument.newEPMDocument (cadDoc.getNumber (),cadDoc.getName (),authorAutoCAD,
		componentType,cadName);
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
	if (logger.isInfoEnabled ()) {
	    logger.info ("创建epm文档处理IBA属性开始... ");
	}
	// process iba attribute
	epmDoc = processIBAHolder (epmDoc,cadDoc,EPMDocument.class);
	if (logger.isInfoEnabled ()) {
	    logger.info ("创建epm文档处理IBA属性结束... ");
	}
	if (logger.isInfoEnabled ()) {
	    logger.info ("创建epm文档处理主内容文件开始... ");
	}
	// upload epmdoc content
	epmDoc = (EPMDocument) saveContents (epmDoc,cadDoc.getAttachments ());
	if (logger.isInfoEnabled ()) {
	    logger.info ("创建epm文档处理主内容文件结束... ");
	}
	return CommonUtils.refresh (epmDoc,EPMDocument.class);
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
	Assert.notNull (cadDoc,"cadDoc is null");
	if (logger.isInfoEnabled ()) {
	    logger.info ("创建epm文档关联部件获取容器和文件夹开始...");
	}
	WTContainer wtcontainer = getWTContainerByName (document.getContainer ().getProduct ().getName ());
	Assert.notNull (wtcontainer,"wtcontainer is null");

	Folder folder = getFolder (document.getContainer ().getFolder ().getName (),wtcontainer);
	Assert.notNull (folder,"folder is null");
	if (logger.isInfoEnabled ()) {
	    logger.info ("创建epm文档关联部件获取容器和文件夹结束...");
	    logger.debug ("createPart wtcontainer is -> " + PrintHelper.printContainer (wtcontainer) + " folder is -> "
		    + PrintHelper.printFolder (folder));
	}
	WTPart part = WTPart.newWTPart (cadDoc.getNumber (),cadDoc.getName ());
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

    public static HashMap<String, String> downLoad(ContentHolder contentHolder, String targetPath, String roleType) {
	HashMap<String, String> contentMap = new HashMap<String, String> ();
	if (contentHolder == null) return contentMap;
	logger.debug ("downLoad param contentHolder is -> " + contentHolder + " roleType is -> " + roleType);
	File file = new File (targetPath);
	if (!file.exists ()) {
	    file.mkdirs ();
	}
	try {
	    if (StringUtils.equals (roleType,"0") || StringUtils.equals (roleType,"2")) {
		QueryResult qrPrimary = wt.content.ContentHelper.service.getContentsByRole (contentHolder,
			ContentRoleType.PRIMARY);
		logger.debug ("qrPrimary qr size is -> " + qrPrimary.size ());
		while (qrPrimary.hasMoreElements ()) {
		    ApplicationData primaryData = (ApplicationData) qrPrimary.nextElement ();
		    String primaryFile = primaryData.getFileName ();
		    if (StringUtils.equals (primaryFile,"{$CAD_NAME}")) {
			primaryFile = ( (EPMDocument) contentHolder ).getCADName ();
		    }
		    logger.debug ("primaryData name is -> " + primaryFile + " format data name is -> "
			    + primaryData.getFormat ().getDataFormat ().getFormatName ());
		    String primaryPath = targetPath + File.separator + primaryFile;
		    logger.debug ("primaryPath is -> " + primaryPath);
		    wt.content.ContentServerHelper.service.writeContentStream (primaryData,primaryPath);

		    contentMap.put (primaryFile,primaryPath);
		}
	    }
	    if (StringUtils.equals (roleType,"1") || StringUtils.equals (roleType,"2")) {
		QueryResult qrSecondary = wt.content.ContentHelper.service.getContentsByRole (contentHolder,
			ContentRoleType.SECONDARY);
		logger.debug ("qrSecondary qr size is -> " + qrSecondary.size ());
		while (qrSecondary.hasMoreElements ()) {
		    ApplicationData secondaryData = (ApplicationData) qrSecondary.nextElement ();
		    String secondaryFile = secondaryData.getFileName ();
		    logger.debug ("secondaryData name is -> " + secondaryFile + " format data name is -> "
			    + secondaryData.getFormat ().getDataFormat ().getFormatName ());
		    String secondaryPath = targetPath + File.separator + secondaryFile;
		    logger.debug ("secondaryPath is -> " + secondaryPath);
		    wt.content.ContentServerHelper.service.writeContentStream (secondaryData,secondaryPath);

		    contentMap.put (secondaryFile,secondaryPath);
		}
	    }
	}
	catch(WTException e) {
	    e.printStackTrace ();
	}
	catch(IOException e) {
	    e.printStackTrace ();
	}
	return contentMap;
    }

    /**
     * Get base filename from a full path filename.
     *
     * <pre>
     *   Example Usage:
     *    FileUtil.getBaseFilename( "C:\myFolder\myFile.txt" ); //returns "myFile.txt"
     * </pre>
     *
     * <BR>
     * <BR>
     * <B>Supported API: </B>false
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

    public static WTPart getLatestWTPart(String partNumber, String view, String state) throws WTException {
	if (partNumber == null || partNumber.equals ("")) {
	    logger.debug ("partNumber is null ...");
	    return null;
	}

	if (( view == null || view.equals ("") ) && ( state == null || state.equals ("") )) {
	    logger.debug ("view and state is all null");
	    return WTPartUtilities.getWTPart (partNumber);
	}

	WTPartStandardConfigSpec configSpec = WTPartStandardConfigSpec.newWTPartStandardConfigSpec (
		view == null || view.equals ("") ? null : ViewHelper.service.getView (view),
		state == null || state.equals ("") ? null : State.toState (state));
	return WTPartUtilities.getWTPart (partNumber,configSpec);
    }

    public static EPMDocument getDocumentByCadName(String name) throws WTException {
	return getDocumentByCadName (name,null);
    }

    @SuppressWarnings("deprecation")
    public static EPMDocument getDocumentByCadName(String name, ConfigSpec configSpec) throws WTException {
	if (configSpec == null) {
	    configSpec = new LatestConfigSpec ();
	}

	QuerySpec querySpec = new QuerySpec (EPMDocument.class);
	int [] fromIndicies = { 0, -1 };
	querySpec.appendWhere (new SearchCondition (EPMDocument.class,EPMDocument.CADNAME,SearchCondition.EQUAL,name),
		fromIndicies);
	querySpec = configSpec.appendSearchCriteria (querySpec);

	QueryResult qr = PersistenceHelper.manager.find (querySpec);
	qr = configSpec.process (qr);

	return (EPMDocument) ( qr.hasMoreElements () ? qr.nextElement () : null );
    }

    public static EPMDocument getDocumentByName(String name) throws WTException {
	return getDocumentByName (name,null);
    }

    @SuppressWarnings("deprecation")
    public static EPMDocument getDocumentByName(String name, ConfigSpec configSpec) throws WTException {
	if (configSpec == null) {
	    configSpec = new LatestConfigSpec ();
	}

	QuerySpec querySpec = new QuerySpec (EPMDocument.class);
	int [] fromIndicies = { 0, -1 };
	querySpec.appendWhere (new SearchCondition (EPMDocument.class,EPMDocument.NAME,SearchCondition.EQUAL,name),
		fromIndicies);
	querySpec = configSpec.appendSearchCriteria (querySpec);

	QueryResult qr = PersistenceHelper.manager.find (querySpec);
	qr = configSpec.process (qr);

	return (EPMDocument) ( qr.hasMoreElements () ? qr.nextElement () : null );
    }

    public static EPMDocument getDocumentByNumber(String number) throws WTException {
	return getDocumentByNumber (number,null);
    }

    @SuppressWarnings("deprecation")
    public static EPMDocument getDocumentByNumber(String number, ConfigSpec configSpec) throws WTException {
	if (configSpec == null) {
	    configSpec = new LatestConfigSpec ();
	}

	QuerySpec querySpec = new QuerySpec (EPMDocument.class);
	int [] fromIndicies = { 0, -1 };
	querySpec.appendWhere (new SearchCondition (EPMDocument.class,EPMDocument.NUMBER,SearchCondition.EQUAL,number),
		fromIndicies);
	querySpec = configSpec.appendSearchCriteria (querySpec);

	QueryResult qr = PersistenceHelper.manager.find (querySpec);
	qr = configSpec.process (qr);

	return (EPMDocument) ( qr.hasMoreElements () ? qr.nextElement () : null );
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

    /**
     * Get extension from a filename.
     *
     * <pre>
     *   Example Usage:
     *    FileUtil.removeExtension( "C:\myFile.txt" )  // returns ".txt"
     * </pre>
     * 
     * @param String
     *            - name of file
     * @return extension of filename
     **/
    public static String getExtension(String filename) { // NOTE: could call
							 // WTStringUtilites.tail()
	for (int i = filename.length () - 1; i >= 0; i--) {
	    if (filename.charAt (i) == '.') {
		String ext = filename.substring (i + 1,filename.length ());
		return ext;
	    }
	}
	return new String ("");
    }

    /**
     * Return back the Folder for the specified folder path in the specified
     * container. The Folder can be a Cabinet path (/<cabinet name> or SubFolder
     * path (/<cabinet name/<sub folder name>).
     */
    public static Folder getFolder(String folderPath, WTContainer container) throws WTException {
	Folder folder = null;

	if (!folderPath.startsWith (DEFAULT_FOLDER)) {
	    folderPath = folderPath.replace ("/" + container.getName (),DEFAULT_FOLDER);
	}

	if (logger.isDebugEnabled ()) {
	    logger.debug ("getFolder folderPath is -> " + folderPath);
	}

	try {
	    WTContainerRef containerRef = getWTContainerRef (container);
	    folder = FolderHelper.service.getFolder (folderPath,containerRef);
	}
	catch(FolderNotFoundException fnfe) {
	    if (logger.isDebugEnabled ()) {
		fnfe.printStackTrace ();
	    }
	}
	catch(FolderException fe) {
	    if (logger.isDebugEnabled ()) {
		fe.printStackTrace ();
	    }
	}
	return folder;
    }

    public static String getFolderPath(Foldered foldered) throws WTException {
	Folder folder = getParentFolder (foldered);
	String folderPath = FolderHelper.getFolderPath (folder);
	if (folderPath != null) {
	    folderPath = folderPath.replaceFirst ("\\A/?Default","/" + folder.getContainerName ());
	}
	return folderPath;
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
	ObjectIdentifier oid = p.getPersistInfo ().getObjectIdentifier ();
	return oid == null ? 0 : oid.getId ();
    }

    public static Folder getParentFolder(Foldered foldered) {
	Folder folder = null;
	if (foldered.getParentFolder () != null && foldered.getParentFolder ().getObject () != null) {
	    folder = (Folder) foldered.getParentFolder ().getObject ();
	} else if (foldered.getCabinet () != null) {
	    folder = (Folder) foldered.getCabinet ().getObject ();
	}
	return folder;
    }

    @SuppressWarnings("deprecation")
    public static WTContainer getWTContainerByName(String containerName) throws WTException {
	if (containerName == null || containerName.equals ("")) {
	    return null;
	}
	QuerySpec querySpec = new QuerySpec (WTContainer.class);
	SearchCondition sc = new SearchCondition (WTContainer.class,WTContainer.NAME,SearchCondition.EQUAL,
		containerName);
	querySpec.appendWhere (sc,0,-1);
	QueryResult queryResult = PersistenceHelper.manager.find (querySpec);

	if (queryResult == null) {
	    return null;
	}

	if (queryResult.hasMoreElements ()) {
	    return (WTContainer) queryResult.nextElement ();
	} else {
	    return null;
	}
    }

    /**
     * For the specified container object, return back it's reference object
     */
    public static WTContainerRef getWTContainerRef(WTContainer container) throws WTException {
	WTContainerRef containerRef = null;
	if (container != null) {
	    containerRef = WTContainerRef.newWTContainerRef (container);
	}
	return containerRef;
    }

    public static Document initialize(CadDocument cadDocument) throws Exception {
	if (logger.isInfoEnabled ()) {
	    logger.info ("initialize param is -> " + cadDocument);
	}
	Document document = new Document ();
	document.setObject (cadDocument);
	String number = cadDocument.getNumber ();
	number = setExtension (number,"DWG");
	if (logger.isDebugEnabled ()) {
	    logger.debug ("find epmdocument by number is -> " + number);
	}
	EPMDocument epmDoc = getDocumentByNumber (number);
	if (epmDoc == null) {// not exist in plm
	    document.setCadStatus (CadStatus.NOT_EXIST);
	} else {// exist in plm
	    document.setCadStatus (
		    WorkInProgressHelper.isCheckedOut (epmDoc) ? CadStatus.CHECK_OUT : CadStatus.CHECK_IN);
	    document.setName (epmDoc.getName ());
	    document.setNumber (epmDoc.getNumber ());
	    document.setOid (CommonUtils.getPersistableOid (epmDoc));
	    Container container = new Container (buildSimpleProduct (epmDoc),buildSimpleFolder (epmDoc));
	    document.setContainer (container);
	}

	return document;
    }

    @SuppressWarnings("unchecked")
    public static <T extends IBAHolder> T processIBAHolder(IBAHolder ibaHolder, CadDocument cadDoc, Class<T> clazz)
	    throws Exception {
	IBAUtils ibaTool = new IBAUtils (ibaHolder);
	LinkedHashMap<String, Object> ibaMap = setIBAValues (ibaTool,cadDoc,clazz);
	if (logger.isDebugEnabled ()) {
	    logger.debug ("ibaMap is -> " + ibaMap);
	}
	return (T) ibaTool.updateIBAPart2 (ibaHolder);
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

    /**
     * Remove extension from a filename.
     *
     * <pre>
     *   Example Usage:
     *    FileUtil.removeExtension( "C:\myFile.txt" )  // returns "C:\myFile"
     * </pre>
     * 
     * <BR>
     * <BR>
     * <B>Supported API: </B>false
     *
     * @param String
     *            - name of file
     * @return filename with extension removed
     **/
//    public static String removeExtension(String filename) { // NOTE: could call
//							    // WTStringUtilites.trimTail()
//	for (int i = filename.length () - 1; i >= 0; i--) {
//	    if (filename.charAt (i) == '.') {
//		String file = filename.substring (0,i);
//		return file;
//	    }
//	}
//	return filename;
//    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static ContentHolder saveContents(ContentHolder holder, List<Attachment> attachments)
	    throws IOException, WTException {
	File shareDirectory = CommonUtils.getShareDirectory ();
	HashMap contentMap = new HashMap ();
	HashMap secondaryMap = null;
	for (Attachment attachment : attachments) {
	    File file = new File (shareDirectory,attachment.getName ());
	    Assert.isTrue (file.exists (),"File[" + file.getPath () + "] does not exist");
	    Assert.isTrue (file.isFile (),"File[" + file.getPath () + "] is not a file");
	    if (attachment.isPrimary ()) {
		String [] primaryPaths = new String [] { file.getAbsolutePath (), "cad tool upload primary.",
			attachment.getRealName (), attachment.getAbsolutePath () };
		contentMap.put ("primary",primaryPaths);
	    } else {
		if (secondaryMap == null) {
		    secondaryMap = new HashMap ();
		}
		String [] secondaryPaths = new String [] { file.getAbsolutePath (), "cad tool upload secondary.",
			attachment.getRealName (), attachment.getAbsolutePath () };
		secondaryMap.put (attachment.getName (),secondaryPaths);
	    }
	}
	if (secondaryMap != null) {
	    contentMap.put ("secondary",secondaryMap);
	}

	uploadForStream (holder,contentMap);

	return holder;
    }

    /**
     * Create a Document, its corresponding Part, and establish a build rule
     * between them
     * 
     * @throws Exception
     */
    public static WTObject [] saveDocAndPart(Document document) throws Exception {
	// Assert.isTrue(AccessControlHelper.manager..hasAccess(document,
	// AccessPermission.CREATE), CommonUtils
	// .toLocalizedMessage(CustomPrompt.ACCESS_DENIED, document.getNumber(),
	// AccessPermission.CREATE));
	// oid exist
	CadStatus cadStatus = document.getCadStatus ();
	EPMDocument epm = null;
	if (cadStatus == CadStatus.NOT_EXIST) {
	    if (logger.isInfoEnabled ()) {
		logger.info ("创建epm文档开始... ");
	    }
	    epm = createEPMDocument (document);
	    if (logger.isInfoEnabled ()) {
		logger.info ("创建epm文档结束... ");
	    }
	} else {
	    if (logger.isInfoEnabled ()) {
		logger.info ("更新epm文档开始... ");
	    }
	    epm = updateEPMDocument (document);
	    if (logger.isInfoEnabled ()) {
		logger.info ("更新epm文档结束... ");
	    }
	}
	// related wtpart
	WTPart part = null;
	if (document.getRelatedPart ()) {
	    if (logger.isInfoEnabled ()) {
		logger.info ("获取epm文档关联部件开始... ");
	    }
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
	    if (logger.isInfoEnabled ()) {
		logger.info ("获取epm文档关联部件结束... " + PrintHelper.printIterated (part) + " staus isCheckedOut is -> "
			+ WorkInProgressHelper.isCheckedOut (part));
	    }
//	    logger.info ("处理epm文档关联部件IBA属性开始... ");
//	    // process iba attribute
//	    part = processIBAHolder (part,(CadDocument) document.getObject (),WTPart.class);
//	    if (logger.isInfoEnabled ()) {
//		logger.info ("处理epm文档关联部件IBA属性结束... ");
//	    }
	    Assert.notNull (part,"releated part is null");
	}

	// do EPMBuildRule
	if (logger.isDebugEnabled ()) {
	    logger.debug ("EPMDocument is checkout. " + PrintHelper.printIterated (epm) + " status isCheckedOut is -> "
		    + WorkInProgressHelper.isCheckedOut (epm));
	}
	// First, check out the part if epmdocument is checkout state.
	if (WorkInProgressHelper.isCheckedOut (epm)) {
	    part = (WTPart) checkout (part,"Part");
	    if (logger.isDebugEnabled ()) {
		logger.debug ("由于epm文档为检出状态,所以相应要检出关联部件.检出后的关联部件为 " + PrintHelper.printIterated (part)
			+ " status isWorkingCopy is -> " + WorkInProgressHelper.isWorkingCopy (part));
	    }
	}

	EPMBuildRule rule = getBuildRule (epm,part);
	if (logger.isDebugEnabled ()) {
	    logger.debug ("epm isWorkingCopy -> " + WorkInProgressHelper.isWorkingCopy (epm) + " part isWorkingCopy -> "
		    + WorkInProgressHelper.isWorkingCopy (part));
	    if (rule != null) {
		logger.debug (PrintHelper.printIterated (epm) + " <-和-> " + PrintHelper.printIterated (part) + " 已存在-> "
			+ PrintHelper.printEPMBuildRule (rule));
	    }
	}

	if (rule == null) {
	    if (logger.isInfoEnabled ()) {
		logger.info ("构建epm文档与部件关系开始... ");
	    }
	    if (document.getBuildType () == 0) {
		rule = createBuildRule (epm,part);
	    } else {
		rule = createBuildRule (epm,part,document.getBuildType ());
	    }
	    if (logger.isInfoEnabled ()) {
		logger.info ("构建epm文档与部件关系结束... ");
	    }
	}

	// Check in the part if we checked it out in order to create the build
	// rule.
	if (WorkInProgressHelper.isCheckedOut (part)) {
	    if (WorkInProgressHelper.isCheckedOut (epm)) {
		if (logger.isDebugEnabled ()) {
		    logger.debug ("检入epm文档与关联部件...");
		}
		Workable [] workables = checkin (new Workable [] { part, epm },"cad tool update epmdocument.");
		part = (WTPart) workables[0];
		epm = (EPMDocument) workables[1];
	    } else {
		if (logger.isDebugEnabled ()) {
		    logger.debug ("检入关联部件...");
		}
		part = (WTPart) checkin (part,"Part");
	    }
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
	    if (logger.isDebugEnabled ()) {
		logger.debug ("刷新EPMBuildRule...");
	    }
	    rule = (EPMBuildRule) PersistenceHelper.manager.refresh (rule);
	}

	WTObject [] objects = new WTObject [3];
	objects[0] = epm;
	objects[1] = part;
	objects[2] = rule;

	return objects;
    }

    /**
     * Set extension for a filename.
     *
     * <pre>
     *   Example Usage:
     *    FileUtil.removeExtension( "C:\myFile", "txt" )  // returns "C:\myFile.txt"
     * </pre>
     * 
     * <BR>
     * <BR>
     * <B>Supported API: </B>false
     *
     * @param filename
     *            name of file
     * @param extension
     *            extension to add to file
     * @return filename with extension added
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

    public static LinkedHashMap<String, Object> setIBAValues(IBAUtils ibaTool, CadDocument cadDoc, Class<?> clazz) {
	LinkedHashMap<String, Object> ibaMap = new LinkedHashMap<String, Object> ();
	Field [] fields = cadDoc.getClass ().getDeclaredFields ();
	String ibaTarget = clazz.getSimpleName ();
	for (Field field : fields) {
	    try {
		field.setAccessible (true);
		// value is null,continue
		Object object = field.get (cadDoc);
		if (object == null) {
		    continue;
		}
		// IbaField is null,continue
		IbaField ibaField = field.getAnnotation (IbaField.class);
		if (ibaField == null) {
		    continue;
		}
		// target() contains ibaTarget
		if (ibaField.target ().contains (ibaTarget)) {
		    if (StringUtils.equalsIgnoreCase (ibaField.ibaName (),"default")) {
			continue;
		    }
		    ibaMap.put (ibaField.ibaName (),object.toString ());
		    ibaTool.setIBAValue (ibaField.ibaName (),object.toString ());
		    // if (logger.isDebugEnabled ()) {
		    // logger.debug (
		    // "setIBAValues iba name is -> " + ibaField.ibaName () + "
		    // iba value is -> " + object);
		    // }
		}
	    }
	    catch(Exception e) {
		e.printStackTrace ();
	    }
	}
	return ibaMap;
    }

    public static SimpleDocument undoCheckout(Document document) throws WTException, WTPropertyVetoException {
	if (logger.isInfoEnabled ()) {
	    logger.info ("Undo Checkout of " + document);
	}
	EPMDocument epm = getDocumentByNumber (document.getNumber ());

	EPMDocument originalEpm = (EPMDocument) undoCheckout (epm);

	SimpleDocument simpleDocument = new SimpleDocument (CommonUtils.getPersistableOid (originalEpm),
		originalEpm.getName (),originalEpm.getNumber ());
	simpleDocument.setCadStatus (CadStatus.CHECK_IN);

	if (logger.isInfoEnabled ()) {
	    logger.info ("Undo Checkout success !");
	}
	return simpleDocument;
    }

    public static Persistable [] undoCheckout(Persistable [] objects) throws WTException, WTPropertyVetoException {
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

    public static WTCollection undoCheckouts(WTCollection objects) throws WTException, WTPropertyVetoException {
	WTCollection undone = WorkInProgressHelper.service.undoCheckouts (objects);
	return undone;
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
	Assert.notNull (cadDoc,"cadDoc is null");

	EPMDocument epmDoc = getDocumentByNumber (document.getNumber ());
	Assert.notNull (epmDoc,"epmdocument is null.");
	EPMDocument workingCopy = null;
	if (WorkInProgressHelper.isCheckedOut (epmDoc)) {
	    if (WorkInProgressHelper.isWorkingCopy (epmDoc)) {
		workingCopy = epmDoc;
	    } else {
		workingCopy = (EPMDocument) WorkInProgressHelper.service.workingCopyOf (epmDoc);
	    }
	}
	Assert.notNull (workingCopy,"epmdocument is not checkout.");
	if (logger.isInfoEnabled ()) {
	    logger.info ("更新epm文档处理IBA属性开始... ");
	}
	// process iba attribute
	workingCopy = processIBAHolder (workingCopy,cadDoc,EPMDocument.class);
	if (logger.isInfoEnabled ()) {
	    logger.info ("更新epm文档处理IBA属性结束... ");
	}
	if (logger.isInfoEnabled ()) {
	    logger.info ("更新epm文档处理主内容文件开始... ");
	}
	// upload epmdoc content
	workingCopy = (EPMDocument) saveContents (workingCopy,cadDoc.getAttachments ());
	if (logger.isInfoEnabled ()) {
	    logger.info ("更新epm文档处理主内容文件结束... ");
	}
	return workingCopy;
    }

    public static String uploadForStream(ContentHolder contentHolder, HashMap<String, ?> targetPathMap)
	    throws WTException {
	StringBuffer errorSB = new StringBuffer ();
	logger.debug ("upload param is -> contentHolder=[" + contentHolder + "] targetPathMap=" + targetPathMap);
	if (contentHolder == null || targetPathMap.isEmpty ()) {
	    errorSB.append ("param is null ...... ");
	}
	try {
	    if (targetPathMap.containsKey ("primary")) {
		String [] primaryPaths = (String []) targetPathMap.get ("primary");
		String primaryPath = primaryPaths.length > 0 ? primaryPaths[0] : "";
		File uploadFile = new File (primaryPath);
		String primaryDescription = primaryPaths.length > 1 ? primaryPaths[1] : "";
		String primaryFileName = primaryPaths.length > 2 ? primaryPaths[2] : uploadFile.getName ();
		String uploadPath = primaryPaths.length > 3 ? primaryPaths[3] : primaryPath;
		if (logger.isDebugEnabled ()) {
		    logger.debug ("primaryPath is -> " + primaryPath + " primaryDescription is -> " + primaryDescription
			    + " primaryFileName is -> " + primaryFileName + " uploadPath is -> " + uploadPath);
		}
		if (uploadFile.exists () && uploadFile.isFile ()) {
		    QueryResult qrPrimary = wt.content.ContentHelper.service.getContentsByRole (contentHolder,
			    ContentRoleType.PRIMARY);
		    while (qrPrimary.hasMoreElements ()) {
			ApplicationData primaryData = (ApplicationData) qrPrimary.nextElement ();
			ContentServerHelper.service.deleteContent (contentHolder,primaryData);
		    }
		    ApplicationData data = ApplicationData.newApplicationData (contentHolder);
		    data.setFileName (primaryFileName);
		    data.setFileSize (uploadFile.length ());
		    data.setUploadedFromPath (uploadPath);
		    data.setRole (ContentRoleType.PRIMARY);
		    data.setDescription (primaryDescription);
		    if (primaryPath.endsWith (".xls") || primaryPath.endsWith (".xlsx") || primaryPath.endsWith (".XLS")
			    || primaryPath.endsWith (".XLSX")) {
			data.setFormat (DataFormatReference
				.newDataFormatReference (ContentHelper.service.getFormatByName ("Microsoft Excel")));
		    } else if (primaryPath.endsWith (".doc") || primaryPath.endsWith (".docx")
			    || primaryPath.endsWith (".DOC") || primaryPath.endsWith (".DOCX")) {
			data.setFormat (DataFormatReference
				.newDataFormatReference (ContentHelper.service.getFormatByName ("Microsoft Word")));
		    } else if (primaryPath.endsWith (".pdf") || primaryPath.endsWith (".PDF")) {
			data.setFormat (DataFormatReference
				.newDataFormatReference (ContentHelper.service.getFormatByName ("PDF")));
		    } else if (primaryPath.endsWith (".dwg") || primaryPath.endsWith (".DWG")) {
			data.setFormat (DataFormatReference
				.newDataFormatReference (ContentHelper.service.getFormatByName ("DWG")));
		    }
		    FileInputStream is = new FileInputStream (uploadFile);
		    try {
			ContentServerHelper.service.updateContent (contentHolder,data,is);
		    }
		    finally {
			is.close ();
		    }
		    // 上传完成后删除
		    uploadFile.deleteOnExit ();
		} else {
		    errorSB.append ("new file=[" + primaryPath + "] is not exist ...");
		}
	    }

	    if (targetPathMap.containsKey ("secondary")) {
		HashMap<?, ?> attachmentsMap = (HashMap<?, ?>) targetPathMap.get ("secondary");
		if (attachmentsMap != null && !attachmentsMap.isEmpty ()) {
		    QueryResult qrSecondary = wt.content.ContentHelper.service.getContentsByRole (contentHolder,
			    ContentRoleType.SECONDARY);
		    while (qrSecondary.hasMoreElements ()) {
			ApplicationData secondaryData = (ApplicationData) qrSecondary.nextElement ();
			String secondaryFile = secondaryData.getFileName ();
			logger.debug ("secondaryFile is -> " + secondaryFile);
			if (attachmentsMap.containsKey (secondaryFile)) {
			    ContentServerHelper.service.deleteContent (contentHolder,secondaryData);
			}
		    }

		    Iterator<?> iter = attachmentsMap.keySet ().iterator ();
		    HashMap<String, Integer> fileNames = new HashMap<String, Integer> ();
		    while (iter.hasNext ()) {
			String fileName = (String) iter.next ();
			String [] secondaryPaths = (String []) attachmentsMap.get (fileName);
			String secondaryPath = secondaryPaths.length > 0 ? secondaryPaths[0] : "";
			String secondaryDescription = secondaryPaths.length > 1 ? secondaryPaths[1] : "";
			File uploadFile = new File (secondaryPath);
			String contentFileName = secondaryPaths.length > 2 ? secondaryPaths[2] : uploadFile.getName ();
			String uploadPath = secondaryPaths.length > 3 ? secondaryPaths[3] : secondaryPath;
			if (logger.isDebugEnabled ()) {
			    logger.debug ("secondaryPath is -> " + secondaryPath + " secondaryDescription is -> "
				    + secondaryDescription + " contentFileName is -> " + contentFileName
				    + " uploadPath is -> " + uploadPath);
			}
			if (uploadFile.exists () && uploadFile.isFile ()) {
			    ApplicationData data = ApplicationData.newApplicationData (contentHolder);
			    String realFileName = contentFileName;
			    if (fileNames.keySet ().contains (contentFileName)) {
				Integer size = fileNames.get (contentFileName);
				if (contentFileName.indexOf (".") > -1) {
				    realFileName = contentFileName.substring (0,contentFileName.lastIndexOf (".")) + "-"
					    + size + contentFileName.substring (contentFileName.lastIndexOf ("."),
						    contentFileName.length ());
				} else {
				    realFileName = contentFileName.substring (0,contentFileName.length ()) + "-" + size;
				}
				fileNames.put (contentFileName,++size);
			    } else {
				realFileName = contentFileName;
				fileNames.put (contentFileName,1);
			    }
			    data.setFileName (realFileName);
			    data.setFileSize (uploadFile.length ());
			    data.setUploadedFromPath (uploadPath);
			    data.setRole (ContentRoleType.SECONDARY);
			    data.setDescription (secondaryDescription);
			    if (secondaryPath.endsWith (".xls") || secondaryPath.endsWith (".xlsx")
				    || secondaryPath.endsWith (".XLS") || secondaryPath.endsWith (".XLSX")) {
				data.setFormat (DataFormatReference.newDataFormatReference (
					ContentHelper.service.getFormatByName ("Microsoft Excel")));
			    } else if (secondaryPath.endsWith (".doc") || secondaryPath.endsWith (".docx")
				    || secondaryPath.endsWith (".DOC") || secondaryPath.endsWith (".DOCX")) {
				data.setFormat (DataFormatReference.newDataFormatReference (
					ContentHelper.service.getFormatByName ("Microsoft Word")));
			    } else if (secondaryPath.endsWith (".pdf") || secondaryPath.endsWith (".PDF")) {
				data.setFormat (DataFormatReference
					.newDataFormatReference (ContentHelper.service.getFormatByName ("PDF")));
			    } else if (secondaryPath.endsWith (".dwg") || secondaryPath.endsWith (".DWG")) {
				data.setFormat (DataFormatReference
					.newDataFormatReference (ContentHelper.service.getFormatByName ("DWG")));
			    }
			    FileInputStream is = new FileInputStream (uploadFile);
			    try {
				ContentServerHelper.service.updateContent (contentHolder,data,is);
			    }
			    finally {
				is.close ();
			    }
			    // 上传完成后删除
			    uploadFile.deleteOnExit ();
			} else {
			    errorSB.append ("new file=[" + secondaryPath + "] is not exist ...");
			}
		    }
		}
	    }

	}
	catch(Exception e) {
	    e.printStackTrace ();
	    if (e instanceof WTException) {
		throw (WTException) e;
	    } else {
		throw new WTException (e.getLocalizedMessage ());
	    }
	}

	return errorSB.toString ();
    }

    public static EPMBuildRule getBuildRule(EPMDocument document, WTPart part) throws WTException {
	QuerySpec qs = new QuerySpec (EPMBuildRule.class);

	qs.appendWhere (new SearchCondition (EPMBuildRule.class,WTAttributeNameIfc.ROLEA_VERSION_ID,
		SearchCondition.EQUAL,VersionControlHelper.getBranchIdentifier (document)),new int [] { 0 });

	qs.appendAnd ();
	qs.appendWhere (new SearchCondition (EPMBuildRule.class,WTAttributeNameIfc.ROLEB_VERSION_ID,
		SearchCondition.EQUAL,VersionControlHelper.getBranchIdentifier (part)),new int [] { 0 });

	QueryResult rules = PersistenceHelper.manager.find ((StatementSpec) qs);
	int size = rules.size ();
	if (size == 0) {
	    if (logger.isInfoEnabled ()) {
		logger.info ("No build rule is found between the document and the part.");
	    }
	    return null;
	} else {
	    if (size > 1) {
		if (logger.isInfoEnabled ()) {
		    logger.info (
			    "Internal Error: more than one build rule found between the given document and part, " + size);
		}
	    } 
	    return (EPMBuildRule) rules.nextElement ();
	}
    }

    public static EPMBuildRule getBuildRule(WTPart target) throws WTException {
	QueryResult results = getBuildRules (target);
	if (results.size () > 1) {
	    if (logger.isDebugEnabled ()) {
		logger.debug ("More than one build rule found");
	    }
	} else {
	    if (logger.isDebugEnabled ()) {
		logger.debug ("OK");
	    }
	}
	return (EPMBuildRule) results.nextElement ();
    }

    public static EPMBuildRule getBuildRule(EPMDocument source) throws WTException {

	QueryResult results = getBuildRules (source);
	if (results.size () > 1) {
	    if (logger.isDebugEnabled ()) {
		logger.debug ("More than one build rule found");
	    }
	} else {
	    if (logger.isDebugEnabled ()) {
		logger.debug ("OK");
	    }
	}

	return (EPMBuildRule) results.nextElement ();
    }

    public static QueryResult getBuildRules(WTPart part) throws WTException {
	return getBuildRules_ (part);
    }

    public static QueryResult getBuildRules(EPMDocument document) throws WTException {
	return getBuildRules_ (document);
    }

    @SuppressWarnings("deprecation")
    private static QueryResult getBuildRules_(Iterated object) throws WTException {

	QuerySpec qs = new QuerySpec (EPMBuildRule.class);

	String role = ( object instanceof BuildSource ) ? VersionToVersionLink.ROLE_AOBJECT_REF
		: VersionToVersionLink.ROLE_BOBJECT_REF;

	qs.appendWhere (new SearchCondition (EPMBuildRule.class,
		role + "." + VersionReference.KEY + "." + VersionForeignKey.BRANCH_ID,SearchCondition.EQUAL,
		new Long (VersionControlHelper.getBranchIdentifier (object))),new int [] { 0, -1 });

	return PersistenceHelper.manager.find (qs);
    }
    
    /**
     *   Create a WTPartUsageLink between two WTParts, and log the
     *   creation to the console.
     */
    public static WTPartUsageLink createUsageLink(WTPart parent, WTPart child, double amount) throws WTException {
	if (logger.isDebugEnabled ()) {
	    logger.debug ("Creating usage link from " + parent.getName () + " to " + child.getName ());
	}
	WTPartUsageLink link = WTPartUsageLink.newWTPartUsageLink (parent,(WTPartMaster) child.getMaster ());
	Quantity qty = Quantity.newQuantity ();
	qty.setAmount (amount);
	link.setQuantity (qty);
	link = (WTPartUsageLink) PersistenceHelper.manager.save (link);
	return link;
    }
    
    public static List<WTPartUsageLink> getUsageLinks(WTPart part) throws WTException {
	List<WTPartUsageLink> usageLinks = new ArrayList<WTPartUsageLink> ();
	QueryResult qr = StructHelper.service.navigateUses (part,WTPartUsageLink.class, false/* onlyOtherSide */);
	while (qr.hasMoreElements () == true) {
	    WTPartUsageLink usageLink = (WTPartUsageLink) qr.nextElement ();
	    usageLinks.add (usageLink);
	}
	return usageLinks;
    }
    
    public static WTPartUsageLink getWTPartUsageLink(WTPart parentPart, WTPartMaster childPartMaster)
	    throws WTException {
	if (parentPart == null || childPartMaster == null) {
	    throw new WTException ("Parent or children part is null");
	}

	Enumeration<?> usageEnum = PersistenceHelper.manager.navigate (parentPart,WTPartUsageLink.USES_ROLE,
		WTPartUsageLink.class,false);

	WTPartUsageLink partUsageLink = null;

	while (usageEnum.hasMoreElements ()) {
	    WTPartUsageLink link = (WTPartUsageLink) usageEnum.nextElement ();
	    if (childPartMaster.equals (link.getRoleBObject ())) {
		partUsageLink = link;
	    }
	    if (partUsageLink != null) {
		break;
	    }
	}

	// Return the first found part master (suppose to have only for each number)
	if (partUsageLink != null) {
	    return partUsageLink;
	}
	if (logger.isDebugEnabled ()) {
	    logger.debug ("Usage not found: " + parentPart + "->" + childPartMaster);
	}
	return null;
    }
    
    public static PartCategory getPartCategory (Document document) {
	CadDocument cadDocument = (CadDocument) document.getObject ();
	return getPartCategory (cadDocument);
    }
    
    public static PartCategory getPartCategory (CadDocument cadDocument) {
	String number = cadDocument.getNumber ();
	String type = cadDocument.getSource ();
	if (!StringUtils.isEmpty (number)) {
	    if (StringUtils.contains (type,"外购件")) {
		return PartCategory.BUY;
	    } else if (StringUtils.isEmpty (type) || StringUtils.contains (type,"自制件")) {
		return PartCategory.MAKE;  
	    }
	}
	return null;  
    }
    
}
