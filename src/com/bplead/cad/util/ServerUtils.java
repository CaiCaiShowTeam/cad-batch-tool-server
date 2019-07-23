package com.bplead.cad.util;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;

import com.bplead.cad.bean.BOMInfo;
import com.bplead.cad.bean.DataContent;
import com.bplead.cad.bean.SimpleDocument;
import com.bplead.cad.bean.SimpleFolder;
import com.bplead.cad.bean.SimplePdmLinkProduct;
import com.bplead.cad.bean.io.CADLink;
import com.bplead.cad.bean.io.CadDocument;
import com.bplead.cad.bean.io.CadDocuments;
import com.bplead.cad.bean.io.CadStatus;
import com.bplead.cad.bean.io.Container;
import com.bplead.cad.bean.io.Document;
import com.bplead.cad.bean.io.Documents;
import com.ptc.windchill.uwgm.common.util.PrintHelper;

import priv.lee.cad.bean.HandleResult;
import priv.lee.cad.util.Assert;
import priv.lee.cad.util.StringUtils;
import wt.epm.EPMDocument;
import wt.fc.Persistable;
import wt.fc.PersistenceHelper;
import wt.fc.QueryResult;
import wt.fc.WTObject;
import wt.fc.collections.WTHashSet;
import wt.folder.Folder;
import wt.folder.FolderHelper;
import wt.folder.SubFolder;
import wt.inf.container.ContainerSpec;
import wt.inf.container.WTContainerHelper;
import wt.inf.container.WTContainerRef;
import wt.log4j.LogR;
import wt.method.RemoteAccess;
import wt.org.WTPrincipal;
import wt.org.WTPrincipalReference;
import wt.org.WTUser;
import wt.part.WTPart;
import wt.part.WTPartHelper;
import wt.part.WTPartUsageLink;
import wt.pdmlink.PDMLinkProduct;
import wt.pom.Transaction;
import wt.session.SessionHelper;
import wt.util.WTException;
import wt.util.WTPropertyVetoException;
import wt.vc.wip.WorkInProgressHelper;
import wt.vc.wip.Workable;

public class ServerUtils implements RemoteAccess, Serializable {

    private static final String DEFAULT_FOLDER = "/Default";
    public static final String EXCEPTION_RB = "com.bplead.cad.resource.CADToolExceptionRB_zh_CN";
    private static final Locale locale = Locale.CHINESE;
    private static Logger logger = LogR.getLogger (ServerUtils.class.getName ());
    private static final String NAVIGATION_RB = "com.ptc.core.ui.navigationRB";
    private static final long serialVersionUID = 3944141455864195993L;
    private static final String WELCOME = "WELCOME";

    public static HandleResult<Documents> initialize(CadDocuments cadDocumets) {
	HandleResult<Documents> result = null;
	try {
	    Assert.notNull (cadDocumets,"Error to get documents");
	    Assert.notNull (cadDocumets.getCadDocs (),"Error to getDocuments of documents");

	    List<CadDocument> cadDocumentL = cadDocumets.getCadDocs ();
	    if (logger.isInfoEnabled ()) {
		logger.info ("initialize data is -> "
			+ ( cadDocumentL == null ? "cadDocumentL is null " : cadDocumentL.size () ));
	    }
	    List<Document> documentL = new ArrayList<Document> ();
	    for (int i = 0; i < cadDocumentL.size (); i++) {
		CadDocument cadDocument = cadDocumentL.get (i);
		if (logger.isDebugEnabled ()) {
		    logger.debug ("current processor order is ->  " + ( i + 1 ) + " cadDocument is -> " + cadDocument);
		}
		Document document = new Document ();
		document.setObject (cadDocument);
		
		String epmnumber= CADHelper.buildEPMDocumentNumber (document);
		
		if (logger.isDebugEnabled ()) {
		    logger.debug ("initialize 搜索EPMDocument的编号为 is -> " + epmnumber);
		}
		EPMDocument epm = CADHelper.getDocumentByNumber (epmnumber);
		if (epm == null) {
		    if (logger.isDebugEnabled ()) {
			logger.debug ("图纸编号[" + epmnumber + "]的对象在系统中不存在.");
		    }
		    document.setCadStatus (CadStatus.NOT_EXIST);
		} else {
		    if (logger.isDebugEnabled ()) {
			logger.debug ("epm display is -> " + PrintHelper.printIterated (epm));
		    }
		    buildDocumentByEPMDocument (document,epm);
		}
		documentL.add (document);
	    }
	    Documents documents = new Documents ();
	    documents.setDocuments (documentL);
	    result = HandleResult.toSuccessedResult (documents);
	}
	catch(Exception e) {
	    result = HandleResult.toErrorResult (e);
	    e.printStackTrace ();
	}
	return result;
    }

    public static void buildDocumentByEPMDocument(Document document, EPMDocument epm) throws WTException {
	document.setNumber (epm.getNumber ());
	document.setName (epm.getName ());
	document.setOid (CommonUtils.getPersistableOid (epm));
	Container container = new Container ();
	SimplePdmLinkProduct product = new SimplePdmLinkProduct (CommonUtils.getPersistableOid (epm.getContainer ()),
		epm.getContainerName ());
	container.setProduct (product);
	document.setContainer (container);
	if (WorkInProgressHelper.isCheckedOut (epm)) {
	    document.setCadStatus (CadStatus.CHECK_OUT);
	    if (WorkInProgressHelper.isWorkingCopy (epm)) {
		EPMDocument originalEpm = (EPMDocument) WorkInProgressHelper.service.originalCopyOf (epm);
		SimpleFolder folder = new SimpleFolder (
			CommonUtils.getPersistableOid (CADHelper.getParentFolder (originalEpm)),
			CADHelper.getFolderPath (originalEpm));
		container.setFolder (folder);
	    } else {
		SimpleFolder folder = new SimpleFolder (CommonUtils.getPersistableOid (CADHelper.getParentFolder (epm)),
			CADHelper.getFolderPath (epm));
		container.setFolder (folder);
	    }
	} else {
	    document.setCadStatus (CadStatus.CHECK_IN);
	    SimpleFolder folder = new SimpleFolder (CommonUtils.getPersistableOid (CADHelper.getParentFolder (epm)),
		    CADHelper.getFolderPath (epm));
	    container.setFolder (folder);
	}
    }

    public static HandleResult<String> validateComfirm(Documents documents) throws WTException {
	StringBuffer buf = new StringBuffer ();
	List<Integer> checkRows = documents.getCheckRows ();
	if (logger.isDebugEnabled ()) {
	    logger.debug ("checkin checkRows is -> " + checkRows);
	}
	List<Document> docList = documents.getDocuments ();
	for (int i = 0; i < docList.size (); i++) {
	    if (!checkRows.contains (i)) {
		continue;
	    }
	    Document document = docList.get (i);
	    if (logger.isDebugEnabled ()) {
		logger.debug ("current processor order is ->  " + ( i + 1 ));
	    }

	    String partNumber = CADHelper.getAssociatePartNumber (document);
	    if (logger.isDebugEnabled ()) {
		logger.debug ("getAssociatePart partNumber is -> " + partNumber);
	    }
	    WTPart part = CADHelper.getLatestWTPart (partNumber,"Design",null);
	    // 如果部件已存在,检查是否关联drw或者其他autoCAD文档
	    if (part != null) {
		List<EPMDocument> list = CADHelper.get2Drawing (part,document);
		if (list != null && !list.isEmpty ()) {
		    for (EPMDocument epm : list) {
			buf.append ("关联部件编号为[" + partNumber + "]的部件在系统中已关联编号为" + epm.getNumber () + "drw文件或者其他AutoCAD图纸. \n");
		    }
		}
	    }
	}
	return HandleResult.toSuccessedResult (buf.toString ());
    }

    public static HandleResult<Boolean> checkin(Documents documents) {
	HandleResult<Boolean> result = null;
	Transaction tran = null;
	try {
	    tran = new Transaction ();
	    tran.start ();

	    Assert.notNull (documents,"Error to get documents");
	    Assert.notNull (documents.getDocuments (),"Error to getDocuments of documents");

	    List<Integer> checkRows = documents.getCheckRows ();
	    if (logger.isDebugEnabled ()) {
		logger.debug ("checkin checkRows is -> " + checkRows);
	    }
	    Assert.notNull (checkRows,"no choose rows...");
	    WTHashSet wtHashSet = new WTHashSet ();

	    List<Document> docList = documents.getDocuments ();
	    // 暂时存储多页图的图纸待处理
	    LinkedHashMap<String, List<Document>> mutiMap = new LinkedHashMap<String, List<Document>> ();
	    for (int i = 0; i < docList.size (); i++) {
		if (!checkRows.contains (i)) {
		    continue;
		}
		Document document = docList.get (i);
		if (logger.isDebugEnabled ()) {
		    logger.debug ("current processor order is ->  " + ( i + 1 ));
		}
		// TODO 校验图纸编号

		CadDocument cadDocument = (CadDocument) document.getObject ();
		Integer pageSize = Integer.valueOf (cadDocument.getPageSize ());
		// 总页码大于1说明是多页图
		if (pageSize > 1) {
		    String tempNumber = CADHelper.getAssociatePartNumber (cadDocument);
		    List<Document> tempList = mutiMap.get (tempNumber);
		    if (tempList == null) {
			tempList = new ArrayList<Document> ();
			tempList.add (document);
			mutiMap.put (tempNumber,tempList);
		    } else {
			tempList.add (document);
		    }
		    continue;
		}

		// save or update EPMDocument and related WTPart
		WTObject [] objects = CADHelper.saveDocAndPart (document);
		if (logger.isDebugEnabled ()) {
		    logger.debug (
			    objects.length > 0 ? PrintHelper.printPersistable (objects[0]) : "epmdocument is null.");
		    logger.debug (objects.length > 1 ? PrintHelper.printPersistable (objects[1]) : "wtpart is null.");
		    logger.debug (
			    objects.length > 2 ? PrintHelper.printPersistable (objects[2]) : "buildrule is null.");
		}
		//收集检入后的EPMDocument
		if (objects.length > 0) {
		    wtHashSet.add (objects[0]);
		}
	    }

	    // 处理多页图纸的检入
	    if (logger.isInfoEnabled ()) {
		logger.info ("mutiMap is -> " + mutiMap);
	    }

	    for (Map.Entry<String, List<Document>> entry : mutiMap.entrySet ()) {
		String number = entry.getKey ();
		List<Document> list = entry.getValue ();
		Object [] objects = CADHelper.saveDocAndPartForMuti (number,list);
		if (logger.isDebugEnabled ()) {
		    logger.debug (objects.length > 0 ? PrintHelper.printPersistable ((Persistable) objects[0]) : "wtpart is null.");
		}
		//收集检入后的EPMDocument
		if (objects.length > 1) {
		    Workable[] checkinObjects = objects[1] == null ? null : (Workable []) objects[1];
		    if (checkinObjects != null) {
			for (Workable workable : checkinObjects) {
			    if (workable instanceof EPMDocument) {
				wtHashSet.add (workable);
			    }
			}
		    }
		}
	    }

	    if (logger.isDebugEnabled ()) {
		logger.debug ("构建BOM结构开始...");
	    }
	    LinkedHashMap<WTPart, List<WTPartUsageLink>> usageLinkMap = new LinkedHashMap<WTPart, List<WTPartUsageLink>> ();
	    StringBuffer buf = new StringBuffer ();
	    // build bom TODO after of checkin
	    for (int i = 0; i < docList.size (); i++) {
		if (!checkRows.contains (i)) {
		    continue;
		}
		Document document = docList.get (i);
		CadDocument cadDocument = (CadDocument) document.getObject ();
		List<CADLink> links = cadDocument.getDetail ();
		if (logger.isDebugEnabled ()) {
		    logger.debug ("links is -> " + links);
		}
		if (links == null || links.isEmpty ()) {
		    continue;
		}
		String parentNumber = cadDocument.getNumber ();
		parentNumber = CADHelper.removeSuffix (parentNumber,null,true);
		WTPart parentPart = CADHelper.getLatestWTPart (parentNumber,"Design",null);
		if (parentPart == null) {
		    buf.append ("编号为[" + parentNumber + "]的部件在系统中不存在,不能为其创建BOM.\n");
		    continue;
		}
		if (logger.isDebugEnabled ()) {
		    logger.debug ("parentPart is -> " + parentPart.getDisplayIdentifier () + " isCheckout is -> "
			    + WorkInProgressHelper.isCheckedOut (parentPart));
		}
		//检出父部件
		WTPart copyPart = parentPart;
		if (!WorkInProgressHelper.isCheckedOut (copyPart)) {
		    copyPart = (WTPart) CADHelper.checkout (copyPart,"cad tool build bom.");
		} else if (!WorkInProgressHelper.isWorkingCopy (copyPart)) {
		    copyPart = (WTPart) WorkInProgressHelper.service.workingCopyOf (copyPart);
		}
		
		//删除老版本的usageLink
		QueryResult qr = WTPartHelper.service.getUsesWTPartMasters (copyPart);
		while (qr.hasMoreElements ()) {
		    WTPartUsageLink usageLink = (WTPartUsageLink)qr.nextElement();
		    PersistenceHelper.manager.delete (usageLink);
		}
		
		if (logger.isDebugEnabled ()) {
		    logger.debug ("copyPart is -> " + copyPart.getDisplayIdentifier () + " isCheckout is -> "
			    + WorkInProgressHelper.isCheckedOut (copyPart));
		}
		
		for (CADLink link : links) {
		    String childNumber = link.getNumber ();
		    if (StringUtils.isEmpty (childNumber == null ? null : childNumber.trim ())) {
			continue;
		    }
		    WTPart childPart = CADHelper.getLatestWTPart (childNumber,"Design",null);
		    if (childPart == null) {
			buf.append ("编号为[" + childNumber + "]的部件在系统中不存在,不能添加为BOM部件.\n");
			continue;
		    }
		    
		    //新建BOM
		    WTPartUsageLink usageLink = CADHelper.createUsageLink (copyPart,childPart,
				Double.valueOf (link.getQuantity ()));
		    List<WTPartUsageLink> usageLinks = usageLinkMap.get (copyPart);
		    if (usageLinks == null) {
			usageLinks = new ArrayList<WTPartUsageLink> ();
			usageLinks.add (usageLink);
			usageLinkMap.put (copyPart,usageLinks);
		    } else {
			usageLinks.add (usageLink);
		    }
		    
//		    // 找到父部件与子部件的usageLink
//		    WTPartUsageLink oldLink = CADHelper.getWTPartUsageLink (copyPart,
//			    (WTPartMaster) childPart.getMaster ());
//		    // 新建
//		    if (oldLink == null) {
//			WTPartUsageLink usageLink = CADHelper.createUsageLink (copyPart,childPart,
//				Double.valueOf (link.getQuantity ()));
//			List<WTPartUsageLink> usageLinks = usageLinkMap.get (copyPart);
//			if (usageLinks == null) {
//			    usageLinks = new ArrayList<WTPartUsageLink> ();
//			    usageLinks.add (usageLink);
//			    usageLinkMap.put (copyPart,usageLinks);
//			} else {
//			    usageLinks.add (usageLink);
//			}
//		    } // 更新
//		    else {
//			// 更新link
//			Double newQuantity = Double.valueOf (link.getQuantity ());
//			Double oldQuantity = oldLink.getQuantity ().getAmount ();
//			if (logger.isDebugEnabled ()) {
//			    logger.debug ("newQuantity is -> " + newQuantity + " oldQuantity is -> " + oldQuantity);
//			}
//			// 如果更新前后数量不一致则更新
//			if (newQuantity != oldQuantity) {
//			    Quantity quantity = Quantity.newQuantity (newQuantity,oldLink.getQuantity ().getUnit ());
//			    oldLink.setQuantity (quantity);
//			    PersistenceServerHelper.manager.update (oldLink);
//			}
//			List<WTPartUsageLink> usageLinks = usageLinkMap.get (copyPart);
//			if (usageLinks == null) {
//			    usageLinks = new ArrayList<WTPartUsageLink> ();
//			    usageLinks.add (oldLink);
//			    usageLinkMap.put (copyPart,usageLinks);
//			} else {
//			    usageLinks.add (oldLink);
//			}
//		    }
		}
	    }

	    if (!StringUtils.isEmpty (buf.toString ())) {
		throw new WTException (buf.toString ());
	    }
	    if (logger.isDebugEnabled ()) {
		logger.debug ("构建BOM结构结束...");
	    }

	    // 检入被检出的父部件
	    if (logger.isDebugEnabled ()) {
		logger.debug ("usageLinkMap is -> " + usageLinkMap);
	    }
	    for (Map.Entry<WTPart, List<WTPartUsageLink>> entry : usageLinkMap.entrySet ()) {
		WTPart part = entry.getKey ();
		if (WorkInProgressHelper.isCheckedOut (part)) {
		    CADHelper.checkin (part,"CAD工具构建BOM检入.");
		}
	    }
	    
	    // 此处嵌入发布表示法代码 TODO
	    CADHelper.publish (wtHashSet);

	    result = HandleResult.toSuccessedResult (true);

	    tran.commit ();
	    tran = null;
	}
	catch(Exception e) {
	    result = HandleResult.toErrorResult (e);
	    e.printStackTrace ();
	}
	finally {
	    if (tran != null) {
		tran.rollback ();
	    }

	    if (result == null) {
		result = HandleResult.toUnExpectedResult ();
	    }
	}
	return result;
    }

    public static HandleResult<List<SimpleDocument>> undoCheckout(Documents documents) {
	HandleResult<List<SimpleDocument>> result = null;
	Transaction tran = null;
	try {
	    tran = new Transaction ();
	    tran.start ();

	    Assert.notNull (documents,"Error to get documents");
	    Assert.notNull (documents.getDocuments (),"Error to getDocuments of documents");
	    List<Integer> checkRows = documents.getCheckRows ();
	    if (logger.isDebugEnabled ()) {
		logger.debug ("undocheckout checkRows is -> " + checkRows);
	    }
	    Assert.notNull (checkRows,"no choose rows...");
	    List<Document> docList = documents.getDocuments ();
	    List<SimpleDocument> returnList = new ArrayList<SimpleDocument> ();
	    for (int i = 0; i < docList.size (); i++) {
		if (!checkRows.contains (i)) {
		    continue;
		}
		Document document = docList.get (i);
		if (logger.isDebugEnabled ()) {
		    logger.debug ("current processor order is ->  " + ( i + 1 ));
		}
		returnList.add (CADHelper.undoCheckout (document));
	    }
	    result = HandleResult.toSuccessedResult (returnList);

	    tran.commit ();
	    tran = null;
	}
	catch(Exception e) {
	    result = HandleResult.toErrorResult (e);
	    e.printStackTrace ();
	}
	finally {
	    if (tran != null) {
		tran.rollback ();
	    }

	    if (result == null) {
		result = HandleResult.toUnExpectedResult ();
	    }
	}
	return result;
    }

    public static HandleResult<List<SimpleDocument>> checkout(Documents documents) {
	HandleResult<List<SimpleDocument>> result = null;
	Transaction tran = null;
	try {
	    tran = new Transaction ();
	    tran.start ();

	    Assert.notNull (documents,"Error to get documents");
	    Assert.notNull (documents.getDocuments (),"Error to getDocuments of documents");
	    List<Integer> checkRows = documents.getCheckRows ();
	    if (logger.isDebugEnabled ()) {
		logger.debug ("checkout checkRows is -> " + checkRows);
	    }
	    Assert.notNull (checkRows,"no choose rows...");
	    List<Document> docList = documents.getDocuments ();
	    List<SimpleDocument> returnList = new ArrayList<SimpleDocument> ();
	    for (int i = 0; i < docList.size (); i++) {
		if (!checkRows.contains (i)) {
		    continue;
		}
		Document document = docList.get (i);
		if (logger.isDebugEnabled ()) {
		    logger.debug ("current processor order is ->  " + ( i + 1 ));
		}
		Assert.notNull (document,"Error to get document");
		returnList.add (CADHelper.checkout (document));
	    }
	    result = HandleResult.toSuccessedResult (returnList);

	    tran.commit ();
	    tran = null;
	}
	catch(Exception e) {
	    result = HandleResult.toErrorResult (e);
	    e.printStackTrace ();
	}
	finally {
	    if (tran != null) {
		tran.rollback ();
	    }

	    if (result == null) {
		result = HandleResult.toUnExpectedResult ();
	    }
	}
	return result;
    }

    public static HandleResult<DataContent> checkoutAndDownload(List<SimpleDocument> documents) {
	HandleResult<DataContent> result = null;
	Transaction tran = null;
	try {
	    tran = new Transaction ();
	    tran.start ();

	    result = HandleResult.toSuccessedResult (DocumentUtils.checkoutAndDownload4Zip (documents));

	    tran.commit ();
	    tran = null;
	}
	catch(Exception e) {
	    result = HandleResult.toErrorResult (e);
	    e.printStackTrace ();
	}
	finally {
	    if (tran != null) {
		tran.rollback ();
	    }

	    if (result == null) {
		result = HandleResult.toUnExpectedResult ();
	    }
	}
	return result;
    }

    private static List<SimpleFolder> findFolders(Folder folder, WTContainerRef ref) throws WTException {
	List<SimpleFolder> folders = new ArrayList<SimpleFolder> ();
	if (folder == null || ref == null) {
	    return folders;
	}

	QueryResult result = FolderHelper.service.findSubFolders (folder);
	while (result.hasMoreElements ()) {
	    Object object = result.nextElement ();
	    if (object instanceof SubFolder) {
		SubFolder subFolder = (SubFolder) object;
		SimpleFolder simpleFolder = new SimpleFolder (CommonUtils.getPersistableOid (subFolder),
			subFolder.getName ());
		simpleFolder.setChildren (findFolders (subFolder,ref));
		folders.add (simpleFolder);
	    }
	}
	return folders;
    }

    public static HandleResult<SimpleFolder> getSimpleFolders(SimplePdmLinkProduct product) {
	if (product == null) {
	    return HandleResult.toErrorResult (null,"Product is required");
	}

	HandleResult<SimpleFolder> result = null;
	try {
	    PDMLinkProduct pdmLinkProduct = CommonUtils.getPersistable (product.getOid (),PDMLinkProduct.class);
	    WTContainerRef ref = WTContainerRef.newWTContainerRef (pdmLinkProduct);

	    Folder folder = FolderHelper.service.getFolder (DEFAULT_FOLDER,ref);
	    SimpleFolder rootFolder = new SimpleFolder (CommonUtils.getPersistableOid (folder),folder.getName ());
	    rootFolder.setChildren (findFolders (folder,ref));
	    result = HandleResult.toSuccessedResult (rootFolder);
	}
	catch(Exception e) {
	    result = HandleResult.toErrorResult (e);
	    e.printStackTrace ();
	}
	finally {
	    if (result == null) {
		result = HandleResult.toUnExpectedResult ();
	    }
	}
	return result;
    }

    public static HandleResult<List<SimplePdmLinkProduct>> getSimplePdmLinkProducts() {
	HandleResult<List<SimplePdmLinkProduct>> result = null;
	try {
	    List<SimplePdmLinkProduct> products = new ArrayList<SimplePdmLinkProduct> ();
	    WTPrincipal principal = SessionHelper.manager.getPrincipal ();
	    ContainerSpec cs = new ContainerSpec (PDMLinkProduct.class);
	    cs.setUser (WTPrincipalReference.newWTPrincipalReference (principal));
	    cs.setMembershipState (256);
	    QueryResult qr = WTContainerHelper.service.getContainers (cs);
	    while (qr.hasMoreElements ()) {
		PDMLinkProduct product = (PDMLinkProduct) qr.nextElement ();
		products.add (new SimplePdmLinkProduct (CommonUtils.getPersistableOid (product),product.getName ()));
	    }
	    logger.debug ("principal:" + principal + ",products:" + products);
	    result = HandleResult.toSuccessedResult (products);
	}
	catch(WTException e) {
	    result = HandleResult.toErrorResult (e);
	    e.printStackTrace ();
	}
	catch(WTPropertyVetoException e) {
	    result = HandleResult.toErrorResult (e);
	    e.printStackTrace ();
	}
	finally {
	    if (result == null) {
		result = HandleResult.toUnExpectedResult ();
	    }
	}
	return result;
    }

    public static HandleResult<String> getWelcomeMessage() {
	HandleResult<String> result = null;
	try {
	    WTUser user = (WTUser) SessionHelper.getPrincipal ();
	    ResourceBundle bundle = ResourceBundle.getBundle (NAVIGATION_RB,locale);
	    result = HandleResult
		    .toSuccessedResult (MessageFormat.format (bundle.getString (WELCOME),user.getFullName ()));
	}
	catch(Exception e) {
	    result = HandleResult.toErrorResult (e);
	    e.printStackTrace ();
	}
	finally {
	    if (result == null) {
		result = HandleResult.toUnExpectedResult ();
	    }
	}
	return result;
    }

    public static HandleResult<List<SimpleDocument>> search(String number, String name) {
	return HandleResult.toSuccessedResult (DocumentUtils.search (number,name));
    }

    public static HandleResult<BOMInfo> getBomDetails(String partNumber) {
	HandleResult<BOMInfo> result = null;
	BOMInfo bomInfo = new BOMInfo ();
	if (StringUtils.isEmpty (partNumber)) {
	    return HandleResult.toSuccessedResult (bomInfo);
	}
	try {
	    bomInfo = PartUtils.getBomDetail (partNumber);
	    result = HandleResult.toSuccessedResult (bomInfo);
	}
	catch(Exception e) {
	    result = HandleResult.toErrorResult (e);
	    e.printStackTrace ();
	}
	finally {
	    if (result == null) {
		result = HandleResult.toUnExpectedResult ();
	    }
	}
	return result;
    }

    public static HandleResult<List<SimplePdmLinkProduct>> getPDMInfos() {
	HandleResult<List<SimplePdmLinkProduct>> result = null;
	try {
	    List<SimplePdmLinkProduct> infos = new ArrayList<SimplePdmLinkProduct> ();
	    WTPrincipal localWTPrincipal = SessionHelper.manager.getPrincipal ();
	    ContainerSpec cs = new ContainerSpec (PDMLinkProduct.class);
	    cs.setUser (WTPrincipalReference.newWTPrincipalReference (localWTPrincipal));
	    cs.setMembershipState (256);
	    QueryResult qr = WTContainerHelper.service.getContainers (cs);
	    while (qr.hasMoreElements ()) {
		PDMLinkProduct pdm = (PDMLinkProduct) qr.nextElement ();
		WTPrincipal principal = pdm.getOwner ();
		WTUser user = null;
		if (principal instanceof WTUser) {
		    user = (WTUser) principal;
		} else {
		    continue;
		}
		String modifier = user.getFullName () + "(" + user.getName () + ")";
		String updateDate = CommonUtils.transferTimestampToString (pdm.getModifyTimestamp (),null,null,null);

		SimplePdmLinkProduct info = new SimplePdmLinkProduct ();
		info.setOid (CommonUtils.getPersistableOid (pdm));
		info.setName (pdm.getName ());
		info.setModifyTime (updateDate);
		info.setModifier (modifier);
		infos.add (info);
	    }
	    result = HandleResult.toSuccessedResult (infos);
	}
	catch(Exception e) {
	    result = HandleResult.toErrorResult (e);
	    e.printStackTrace ();
	}
	finally {
	    if (result == null) {
		result = HandleResult.toUnExpectedResult ();
	    }
	}
	return result;
    }
}
