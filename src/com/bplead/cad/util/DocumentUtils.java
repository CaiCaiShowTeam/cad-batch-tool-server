package com.bplead.cad.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import org.apache.log4j.Logger;

import com.bplead.cad.bean.DataContent;
import com.bplead.cad.bean.SimpleDocument;
import com.bplead.cad.constant.CustomPrompt;
import com.ptc.windchill.uwgm.common.util.PrintHelper;

import priv.lee.cad.util.Assert;
import priv.lee.cad.util.StringUtils;
import wt.access.AccessControlHelper;
import wt.access.AccessPermission;
import wt.content.ApplicationData;
import wt.content.ContentHelper;
import wt.content.ContentRoleType;
import wt.content.ContentServerHelper;
import wt.epm.EPMDocument;
import wt.epm.EPMDocumentMaster;
import wt.fc.PersistenceHelper;
import wt.fc.QueryResult;
import wt.method.RemoteAccess;
import wt.query.ConstantExpression;
import wt.query.OrderBy;
import wt.query.QueryException;
import wt.query.QuerySpec;
import wt.query.SearchCondition;
import wt.query.TableColumn;
import wt.session.SessionHelper;
import wt.session.SessionServerHelper;
import wt.session.SessionThread;
import wt.util.WTException;
import wt.vc.Iterated;

public class DocumentUtils implements RemoteAccess {

	public static final int INSTANCE_NUM_LENGTH = 2000;
	public static final String INSTANCE_NUM_SEPERATOR = ",";
	private static final String LIKE = "%";
	private static final Logger logger = Logger.getLogger(DocumentUtils.class);
	private static final String NAME = "name";
	private static final String NUMBER = "documentNumber";
	private static final String ZIP = ".zip";

	public static DataContent checkoutAndDownload4Zip(List<SimpleDocument> documents) throws Exception {
		Assert.notEmpty(documents, "Simple documents are requied");

		File shareDirectory = CommonUtils.getShareDirectory();
		File repository = new File(shareDirectory, CommonUtils.getUUID32());

		List<FutureTask<Boolean>> tasks = new ArrayList<FutureTask<Boolean>>();
		for (SimpleDocument document : documents) {
			EPMDocument epmdocument = CommonUtils.getPersistable(document.getOid(), EPMDocument.class);

			validateAccess(epmdocument, AccessPermission.DOWNLOAD);

			tasks.add(toFutureTask(epmdocument, repository));
		}

		for (FutureTask<Boolean> task : tasks) {
			if (!task.get()) {
				logger.error("1 WTDocument download failed");
			}
		}

		File zipFile = zipFile(shareDirectory, repository);
		return new DataContent(null, zipFile, shareDirectory, true);
	}

	@SuppressWarnings("deprecation")
	public static List<SimpleDocument> search(String number, String name) {
		Assert.isTrue(StringUtils.hasText(number) || StringUtils.hasText(name), "Number or name is requried");

		List<SimpleDocument> docs = new ArrayList<SimpleDocument>();
		boolean enforced = SessionServerHelper.manager.setAccessEnforced(false);
		try {
			QuerySpec query = CommonUtils.getAdvancedQuery();

			int docIndex = query.addClassList(EPMDocumentMaster.class, true);

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
				EPMDocumentMaster master = (EPMDocumentMaster) (((Object[]) result.nextElement())[0]);
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

	private static FutureTask<Boolean> toFutureTask(EPMDocument epmdocument, File repository) {
		FutureTask<Boolean> task = new FutureTask<Boolean>(new CheckoutAndDownloadTask(epmdocument, repository));
		// new SessionThread(task, new SessionContext()).start();
		new SessionThread(task).start();
		return task;
	}

	private static SimpleDocument toSimpleDocument(EPMDocumentMaster master) {
		EPMDocument document = CommonUtils.getLatestObject(master, EPMDocument.class);
		SimpleDocument simpleDoc = new SimpleDocument();
		simpleDoc.setOid(CommonUtils.getPersistableOid(document));
		simpleDoc.setName(document.getName());
		simpleDoc.setNumber(document.getNumber());
		simpleDoc.setVersion(
				document.getVersionIdentifier().getValue() + "." + document.getIterationIdentifier().getValue());
		simpleDoc.setModifyTime(CommonUtils.transferTimestampToString(document.getModifyTimestamp(), null, null, null));
		simpleDoc.setNumber(document.getCreatorFullName() + "(" + document.getCreatorName() + ")");
		return simpleDoc;
	}

	private static void validateAccess(Iterated iterated, AccessPermission permission) {
		Assert.notNull(iterated, "iterated does not exist");
		try {
			Assert.isTrue(AccessControlHelper.manager.hasAccess(iterated, permission), CommonUtils
					.toLocalizedMessage(CustomPrompt.ACCESS_DENIED, PrintHelper.printIterated(iterated), permission));
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
		private EPMDocument epmdocument;
		private File repository;

		public CheckoutAndDownloadTask(EPMDocument epmdocument, File repository) {
			this.epmdocument = epmdocument;
			this.repository = new File(repository, epmdocument.getNumber());
		}

		private void addProperties() {
			try {
				File file = new File(repository, epmdocument.getNumber() + PROPERTIES);
				if (!file.exists()) {
					file.createNewFile();
				}

				Properties props = new Properties();
				props.put(OID, epmdocument.toString());
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
				epmdocument = CommonUtils.checkout(epmdocument, null, EPMDocument.class);

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
			logger.info("EPMDocument[" + CommonUtils.getPersistableOid(epmdocument) + "],role[" + application.getRole()
					+ "],repository[" + appRepo + "]");
			ContentServerHelper.service.writeContentStream(application, appRepo);
		}

		private void download(ContentRoleType role) throws WTException, IOException {
			QueryResult qr = ContentHelper.service.getContentsByRole(epmdocument, role);
			while (qr.hasMoreElements()) {
				ApplicationData application = (ApplicationData) qr.nextElement();
				download(application);
			}
		}
	}
}
