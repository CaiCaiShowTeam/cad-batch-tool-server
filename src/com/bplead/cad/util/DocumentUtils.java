package com.bplead.cad.util;

import java.beans.PropertyVetoException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import org.apache.log4j.Logger;

import com.bplead.cad.bean.DataContent;
import com.bplead.cad.bean.SimpleDocument;
import com.bplead.cad.bean.constant.DesignDocAttribute;
import com.bplead.cad.bean.constant.ManufactorDocAttribute;
import com.bplead.cad.bean.io.Attachment;
import com.bplead.cad.bean.io.CAD;
import com.bplead.cad.bean.io.CAPP;
import com.bplead.cad.bean.io.Document;
import com.bplead.cad.constant.CustomPrompt;

import priv.lee.cad.util.Assert;
import priv.lee.cad.util.StringUtils;
import wt.access.AccessControlHelper;
import wt.access.AccessPermission;
import wt.admin.AdminDomainRef;
import wt.content.ApplicationData;
import wt.content.ContentHelper;
import wt.content.ContentHolder;
import wt.content.ContentRoleType;
import wt.content.ContentServerHelper;
import wt.doc.DepartmentList;
import wt.doc.WTDocument;
import wt.doc.WTDocumentHelper;
import wt.doc.WTDocumentMaster;
import wt.fc.PersistenceHelper;
import wt.fc.QueryResult;
import wt.folder.Folder;
import wt.folder.FolderEntry;
import wt.folder.FolderHelper;
import wt.method.RemoteAccess;
import wt.pdmlink.PDMLinkProduct;
import wt.query.ConstantExpression;
import wt.query.OrderBy;
import wt.query.QueryException;
import wt.query.QuerySpec;
import wt.query.SearchCondition;
import wt.query.TableColumn;
import wt.session.SessionHelper;
import wt.session.SessionServerHelper;
import wt.session.SessionThread;
import wt.type.TypeDefinitionReference;
import wt.type.TypedUtilityServiceHelper;
import wt.util.WTException;
import wt.util.WTPropertyVetoException;

public class DocumentUtils implements RemoteAccess {

	private static Map<String, String> docAttributes = new HashMap<String, String>();
	public static final int INSTANCE_NUM_LENGTH = 2000;
	public static final String INSTANCE_NUM_SEPERATOR = ",";
	private static final String LIKE = "%";
	private static final Logger logger = Logger.getLogger(DocumentUtils.class);
	private static final String MANUFACTOR_REQUIRED_DOC_TYPE = "com.chengliang.gyyqwd";
	private static final String NAME = "NAME";
	private static final String NUMBER = "WTDOCUMENTNUMBER";
	private static final String ZIP = ".zip";
	static {
		initDesignDocAttributes();
		initManufactorDocAttributes();
	}

	public static DataContent checkoutAndDownload4Zip(List<SimpleDocument> documents) throws Exception {
		Assert.notEmpty(documents, "Simple documents are requied");

		File shareDirectory = CommonUtils.getShareDirectory();
		File repository = new File(shareDirectory, CommonUtils.getUUID32());

		List<FutureTask<Boolean>> tasks = new ArrayList<FutureTask<Boolean>>();
		for (SimpleDocument document : documents) {
			WTDocument WTDocument = CommonUtils.getPersistable(document.getOid(), WTDocument.class);

			validate(WTDocument);

			tasks.add(toFutureTask(WTDocument, repository));
		}

		for (FutureTask<Boolean> task : tasks) {
			if (!task.get()) {
				logger.error("1 WTDocument download failed");
			}
		}

		File zipFile = zipFile(shareDirectory, repository);
		return new DataContent(null, zipFile, shareDirectory, true);
	}

	private static String findIBAName(String fieldName) {
		if (!StringUtils.hasText(fieldName)) {
			return null;
		}
		return docAttributes.get(fieldName.toUpperCase());
	}

	private static void initDesignDocAttributes() {
		initDocAttributes(docAttributes, DesignDocAttribute.class);
	}

	private static void initDocAttributes(Map<String, String> docAttributeMap, Class<?> attributeClass) {
		Field[] fields = attributeClass.getDeclaredFields();
		for (Field field : fields) {
			try {
				field.setAccessible(true);
				docAttributeMap.put(field.getName(), (String) field.get(attributeClass));
			} catch (Exception e) {
				logger.error("error to load " + attributeClass + " [" + field.getName() + "]");
			}
		}
	}

	private static void initManufactorDocAttributes() {
		initDocAttributes(docAttributes, ManufactorDocAttribute.class);
	}

	@SuppressWarnings("unchecked")
	private static WTDocument removeContents(WTDocument doc) throws WTException, PropertyVetoException {
		ContentHolder holder = ContentHelper.service.getContents(doc);
		Vector<ApplicationData> datas = ContentHelper.getContentListAll(holder);
		for (int i = 0; i < datas.size(); i++) {
			ApplicationData data = datas.get(i);
			ContentServerHelper.service.deleteContent(holder, data);
		}
		return CommonUtils.refresh(doc, WTDocument.class);
	}

	public static WTDocument save(Document document) throws Exception {
		Assert.notNull(document, "Document is required");
		Assert.isTrue(AccessControlHelper.manager.hasAccess(document, AccessPermission.CREATE), CommonUtils
				.toLocalizedMessage(CustomPrompt.ACCESS_DENIED, document.getNumber(), AccessPermission.CREATE));

		WTDocument doc = WTDocument.newWTDocument();

		// ~ set WTDocument MBA
		doc.setName(document.getName());

		TypeDefinitionReference tdRef = TypedUtilityServiceHelper.service
				.getTypeDefinitionReference(document.getType());
		Assert.notNull(tdRef, "Can not find WTDocument type:" + document.getType());

		PDMLinkProduct product = CommonUtils.getPersistable(document.getContainer().getProduct().getOid(),
				PDMLinkProduct.class);
		Assert.notNull(product, "Product[" + document.getContainer().getProduct().getOid() + "] does not exsit");

		Folder folder = CommonUtils.getPersistable(document.getContainer().getFolder().getOid(), Folder.class);
		Assert.notNull(folder, "Folder[" + document.getContainer().getFolder().getOid() + "] does not exsit");

		doc.setDepartment(DepartmentList.getDepartmentListDefault());
		doc.setContainer(product);
		doc.setDomainRef(AdminDomainRef.newAdminDomainRef(product.getDefaultDomainReference()));
		doc.setTypeDefinitionReference(tdRef);
		FolderHelper.assignLocation((FolderEntry) doc, folder);

		// save WTDocument
		doc = CommonUtils.save(doc, WTDocument.class);
		return setAttributeAndContent(doc, document);
	}

	public static WTDocument saveOrUpdate(Document document) throws Exception {
		Assert.notNull(document, "Document is required");

		WTDocument doc = null;
		if (StringUtils.hasText(document.getOid())) {
			doc = update(document);
		} else {
			doc = save(document);
		}
		return CommonUtils.refresh(doc, WTDocument.class);
	}

	public static List<SimpleDocument> search(String number, String name) {
		Assert.isTrue(StringUtils.hasText(number) || StringUtils.hasText(name), "Number or name is requried");

		List<SimpleDocument> docs = new ArrayList<SimpleDocument>();
		boolean enforced = SessionServerHelper.manager.setAccessEnforced(false);
		try {
			QuerySpec query = CommonUtils.getAdvancedQuery();

			int docIndex = query.addClassList(WTDocumentMaster.class, true);

			String[] alias = new String[1];
			alias[0] = query.getFromClause().getAliasAt(docIndex);

			TableColumn numColumn = new TableColumn(alias[0], NUMBER);
			TableColumn nameColumn = new TableColumn(alias[0], NAME);

			if (StringUtils.hasText(number)) {
				query.appendWhere(
						new SearchCondition(numColumn, SearchCondition.LIKE,
								new ConstantExpression(number.endsWith(LIKE) ? number : number + LIKE)),
						new int[] { 0 });
			}

			if (StringUtils.hasText(name)) {
				if (query.getWhereClause().getCount() > 0) {
					query.appendAnd();
				}
				query.appendWhere(new SearchCondition(nameColumn, SearchCondition.LIKE,
						new ConstantExpression(number.endsWith(LIKE) ? name : name + LIKE)), new int[] { 0 });
			}

			if (StringUtils.hasText(number)) {
				query.appendOrderBy(new OrderBy(numColumn, true), new int[] { 0 });
			} else {
				query.appendOrderBy(new OrderBy(nameColumn, true), new int[] { 0 });
			}
			logger.debug("query:" + query.toString());

			QueryResult result = PersistenceHelper.manager.find(query);
			while (result.hasMoreElements()) {
				WTDocumentMaster master = (WTDocumentMaster) (((Object[]) result.nextElement())[0]);
				docs.add(toSimpleDocument(master));
			}
		} catch (QueryException e) {
			e.printStackTrace();
		} catch (WTException e) {
			e.printStackTrace();
		} finally {
			SessionServerHelper.manager.setAccessEnforced(enforced);
		}
		return docs;
	}

	public static WTDocument setAttributeAndContent(WTDocument doc, Document document)
			throws WTException, IOException, WTPropertyVetoException {
		Assert.notNull(doc, "WTDocument is required");
		Assert.notNull(document, "Document is required");

		Serializable object = document.getObject();
		// set WTDocument IBA
		if (CAD.class.isInstance(object)) {
			doc = setDocumentIBAs(doc, (CAD) object);
		} else {
			doc = setDocumentIBAs(updated(document.getType()), doc, (CAPP) object);
		}

		// set WTDocument content
		doc = setContents(doc, document.getObject().getAttachments());
		return CommonUtils.refresh(doc, WTDocument.class);
	}

	private static WTDocument setContents(WTDocument doc, List<Attachment> attachments)
			throws WTException, IOException {
		doc = CommonUtils.refresh(doc, WTDocument.class);

		File shareDirectory = CommonUtils.getShareDirectory();
		for (Attachment attachment : attachments) {
			File file = new File(shareDirectory, attachment.getName());
			Assert.isTrue(file.exists(), "File[" + file.getPath() + "] does not exist");
			Assert.isTrue(file.isFile(), "File[" + file.getPath() + "] is not a file");

			try {
				ApplicationData application = ApplicationData.newApplicationData(doc);
				application.setUploadedFromPath(attachment.getAbsolutePath());
				if (attachment.isPrimary()) {
					application.setRole(ContentRoleType.PRIMARY);
				} else {
					application.setRole(ContentRoleType.SECONDARY);
				}
				application.setFileSize(file.length());
				application = ContentServerHelper.service.updateContent(doc, application, file.getPath());

				doc = (WTDocument) ContentServerHelper.service.updateHolderFormat(doc);
			} catch (Exception e) {
				logger.error("File[" + file + "] add to WTDocument[" + doc + "] failed");
				e.printStackTrace();
			} finally {
				if (file.exists()) {
					file.delete();
				}
			}
		}
		return doc;
	}

	private static WTDocument setDocumentIBAs(boolean updated, WTDocument doc, CAPP capp)
			throws WTException, WTPropertyVetoException {
		doc = CommonUtils.refresh(doc, WTDocument.class);
		// ~ add all attributes
		IBAUtils utils = new IBAUtils(doc);
		utils.setIBAValue(ManufactorDocAttribute.MANUREGULATIONNAME, capp.getManuRegulationName());
		if (updated) {
			setDocumentIBAs(CAPP.class, utils, capp);
		}
		doc = utils.updateAttributeContainer(doc, WTDocument.class);
		return CommonUtils.modify(doc, WTDocument.class);
	}

	private static void setDocumentIBAs(Class<?> cls, IBAUtils utils, Serializable serializable) {
		Field[] fields = cls.getDeclaredFields();
		for (Field field : fields) {
			try {
				field.setAccessible(true);
				Object object = field.get(serializable);
				if (object == null) {
					continue;
				}

				String name = findIBAName(field.getName());
				if (!StringUtils.hasText(name)) {
					continue;
				}
				utils.setIBAValue(name, object.toString());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static WTDocument setDocumentIBAs(WTDocument doc, CAD cad) throws WTException, WTPropertyVetoException {
		doc = CommonUtils.refresh(doc, WTDocument.class);

		IBAUtils utils = new IBAUtils(doc);
		// add all attributes
		setDocumentIBAs(CAD.class, utils, cad);

		// add xxth1 attribute
		utils.setIBAValue(DesignDocAttribute.DETAILNUM + 1, cad.getDetailNum());

		// ~ add xxthX attribute
		List<String> instanceNums = PartUtils.toInstanceNums(cad.getDetailNum());
		int DETAIL_NUMBER_BEGIN = 2;
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < instanceNums.size(); i++) {
			String instanceNum = instanceNums.get(i);
			if (builder.length() < INSTANCE_NUM_LENGTH) {
				builder.append(instanceNum);
			} else {
				utils.setIBAValue(DesignDocAttribute.DETAILNUM + DETAIL_NUMBER_BEGIN++, builder.toString());
				builder = new StringBuilder(instanceNum);
			}
			builder.append(INSTANCE_NUM_SEPERATOR);

			if (i == instanceNums.size() - 1) {
				utils.setIBAValue(DesignDocAttribute.DETAILNUM + DETAIL_NUMBER_BEGIN++, builder.toString());
			}
		}
		doc = utils.updateAttributeContainer(doc, WTDocument.class);
		return CommonUtils.modify(doc, WTDocument.class);
	}

	private static FutureTask<Boolean> toFutureTask(WTDocument WTDocument, File repository) {
		FutureTask<Boolean> task = new FutureTask<Boolean>(new CheckoutAndDownloadTask(WTDocument, repository));
		// new SessionThread(task, new SessionContext()).start();
		new SessionThread(task).start();
		return task;
	}

	private static SimpleDocument toSimpleDocument(WTDocumentMaster master) {
		WTDocument document = CommonUtils.getLatestObject(master, WTDocument.class);
		return new SimpleDocument(CommonUtils.getPersistableOid(document), document.getName(), document.getNumber());
	}

	private static WTDocument update(Document document) throws Exception {
		Assert.notNull(document, "Document is required");

		WTDocument doc = CommonUtils.getPersistable(document.getOid(), WTDocument.class);
		// rename
		WTDocumentHelper.service.changeWTDocumentMasterIdentity((WTDocumentMaster) doc.getMaster(), document.getName(),
				doc.getNumber(), doc.getOrganization());

		WTDocument workingCopy = CommonUtils.checkout(doc, null, WTDocument.class);

		removeContents(workingCopy);

		setAttributeAndContent(workingCopy, document);

		return CommonUtils.checkin(workingCopy, null, WTDocument.class);
	}

	public static boolean updated(String type) {
		if (type.endsWith(MANUFACTOR_REQUIRED_DOC_TYPE)) {
			return false;
		}
		return true;
	}

	private static void validate(WTDocument document) {
		Assert.notNull(document, "WTDocument does not exist");

		try {
			Assert.isTrue(AccessControlHelper.manager.hasAccess(document, AccessPermission.DOWNLOAD),
					CommonUtils.toLocalizedMessage(CustomPrompt.ACCESS_DENIED, document.getNumber(),
							AccessPermission.DOWNLOAD));
		} catch (WTException e) {
			e.printStackTrace();
		}
	}

	private static File zipFile(File shareDirectory, File repository) {
		File zipFile = new File(shareDirectory, repository.getName() + ZIP);
		CommonUtils.zip(repository, zipFile);
		return zipFile;
	}

	static class CheckoutAndDownloadTask implements Callable<Boolean> {

		private static final String OID = "oid";
		private static final String PROPERTIES = ".properties";
		private static final String TIME = "time";
		private static final String USER = "user";
		private WTDocument document;
		private File repository;

		public CheckoutAndDownloadTask(WTDocument document, File repository) {
			this.document = document;
			this.repository = new File(repository, document.getNumber());
		}

		private void addProperties() {
			try {
				File file = new File(repository, document.getNumber() + PROPERTIES);
				if (!file.exists()) {
					file.createNewFile();
				}

				Properties props = new Properties();
				props.put(OID, document.toString());
				props.put(TIME, new Date().toString());
				props.put(USER, SessionHelper.getPrincipal().toString());
				props.store(new FileOutputStream(file), null);
			} catch (WTException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		@Override
		public Boolean call() throws Exception {
			try {
				document = CommonUtils.checkout(document, null, WTDocument.class);

				// SessionMgr.setAuthenticatedPrincipal(user.getAuthenticationName());
				download(ContentRoleType.PRIMARY);

				download(ContentRoleType.SECONDARY);

				addProperties();

				return true;
			} catch (WTException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return false;
		}

		private void download(ApplicationData application) throws WTException, IOException {
			File roleRepo = new File(repository, application.getRole().getDisplay());
			if (!roleRepo.exists()) {
				roleRepo.mkdirs();
			}

			String appRepo = roleRepo.getPath() + File.separator + application.getFileName();
			logger.info("WTDocument[" + CommonUtils.getPersistableOid(document) + "],role[" + application.getRole()
					+ "],repository[" + appRepo + "]");
			ContentServerHelper.service.writeContentStream(application, appRepo);
		}

		private void download(ContentRoleType role) throws WTException, IOException {
			QueryResult qr = ContentHelper.service.getContentsByRole(document, role);
			while (qr.hasMoreElements()) {
				ApplicationData application = (ApplicationData) qr.nextElement();
				download(application);
			}
		}
	}
}
