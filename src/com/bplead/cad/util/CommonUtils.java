package com.bplead.cad.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;

import com.bplead.cad.constant.CustomPrompt;

import priv.lee.cad.util.Assert;
import wt.access.AccessControlHelper;
import wt.access.AccessPermission;
import wt.doc.WTDocument;
import wt.enterprise.Master;
import wt.fc.Persistable;
import wt.fc.PersistenceHelper;
import wt.fc.QueryResult;
import wt.fc.ReferenceFactory;
import wt.fc.WTReference;
import wt.folder.Folder;
import wt.part.WTPart;
import wt.pom.PersistenceException;
import wt.query.QueryException;
import wt.query.QuerySpec;
import wt.util.WTException;
import wt.util.WTMessage;
import wt.util.WTProperties;
import wt.util.WTPropertyVetoException;
import wt.vc.VersionControlHelper;
import wt.vc.wip.CheckoutLink;
import wt.vc.wip.WorkInProgressHelper;
import wt.vc.wip.Workable;

public class CommonUtils {

    private static final int BUFFER_SIZE = 2 * 1024;
    private static final String SHARE_DIR = "wt.home.share.dir";

    @SuppressWarnings("unchecked")
    public static <T extends Workable> T checkin(Workable workable, String note, Class<T> clatt)
	    throws WTException, WTPropertyVetoException {
	Assert.notNull (workable,"Workable is required");
	Assert.notNull (clatt,"Class<? extends Workable> is required");

	if (!WorkInProgressHelper.isWorkingCopy (workable)) {
	    workable = WorkInProgressHelper.service.workingCopyOf (workable);
	}
	if (WorkInProgressHelper.isCheckedOut (workable)) {
	    workable = WorkInProgressHelper.service.checkin (workable,note);
	}
	return (T) workable;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Workable> T checkout(Workable workable, String note, Class<T> clatt)
	    throws WTException, WTPropertyVetoException {
	Assert.notNull (workable,"Workable is required");
	Assert.notNull (clatt,"Class<? extends Workable> is required");

	if (WorkInProgressHelper.isWorkingCopy (workable)) {
	    return (T) workable;
	}

	if (WorkInProgressHelper.isCheckedOut (workable)) {
	    return (T) WorkInProgressHelper.service.workingCopyOf (workable);
	}

	String number = "";
	if (workable instanceof WTPart) {
	    WTPart wtpart = (WTPart) workable;
	    number = wtpart.getNumber ();
	} else if (workable instanceof WTDocument) {
	    WTDocument wtdoc = (WTDocument) workable;
	    number = wtdoc.getNumber ();
	} else {
	    number = workable.toString ();
	}

	Assert.isTrue (AccessControlHelper.manager.hasAccess (workable,AccessPermission.MODIFY),
		CommonUtils.toLocalizedMessage (CustomPrompt.ACCESS_DENIED,number,AccessPermission.MODIFY));

	Folder checkOutFolder = WorkInProgressHelper.service.getCheckoutFolder ();
	CheckoutLink checkOutLink = WorkInProgressHelper.service.checkout (workable,checkOutFolder,note);
	Workable workingCopy = checkOutLink.getWorkingCopy ();
	if (!WorkInProgressHelper.isWorkingCopy (workingCopy)) {
	    workingCopy = WorkInProgressHelper.service.workingCopyOf (workingCopy);
	}
	return (T) workingCopy;
    }

    private static void compress(File sourceFile, ZipOutputStream zos, String name) throws Exception {
	byte [] buf = new byte [BUFFER_SIZE];
	if (sourceFile.isFile ()) {
	    zos.putNextEntry (new ZipEntry (name));
	    int len;
	    FileInputStream in = new FileInputStream (sourceFile);
	    while (( len = in.read (buf) ) != -1) {
		zos.write (buf,0,len);
	    }
	    zos.closeEntry ();
	    in.close ();
	} else {
	    File [] listFiles = sourceFile.listFiles ();
	    if (listFiles == null || listFiles.length == 0) {
		zos.putNextEntry (new ZipEntry (name + File.separator));
	    } else {
		for (File file : listFiles) {
		    compress (file,zos,name + File.separator + file.getName ());
		}
	    }
	}
    }

    public static QuerySpec getAdvancedQuery() throws QueryException {
	QuerySpec query = new QuerySpec ();
	query.setAdvancedQueryEnabled (true);
	return query;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getLatestObject(Master master, Class<T> clatt) {
	try {
	    QueryResult qr = VersionControlHelper.service.allVersionsOf (master);
	    return (T) qr.nextElement ();
	}
	catch(PersistenceException e) {
	    e.printStackTrace ();
	}
	catch(WTException e) {
	    e.printStackTrace ();
	}
	return null;
    }

    public static WTProperties getLocalProperties() throws IOException {
	return WTProperties.getLocalProperties ();
    }

    @SuppressWarnings("unchecked")
    public static <T> T getPersistable(String oid, Class<T> clatt) {
	Assert.hasText (oid,"Persistable oid is required");

	try {
	    WTReference ref = new ReferenceFactory ().getReference (oid);
	    if (ref != null) {
		return (T) ref.getObject ();
	    }
	}
	catch(WTException e) {
	    e.printStackTrace ();
	}
	return null;
    }

    public static String getPersistableOid(Persistable persistable) {
	Assert.notNull (persistable,"Persistable is required");
	return persistable.getPersistInfo ().getObjectIdentifier ().toString ();
    }

    public static File getShareDirectory() throws IOException {
	String dir = getLocalProperties ().getProperty (SHARE_DIR);
	Assert.hasText (dir,"Share directory needs to config in wt.properties as key[" + SHARE_DIR + "]");

	File file = new File (dir);
	if (!file.exists ()) {
	    file.mkdirs ();
	}
	return file;
    }

    public static String getUUID32() {
	return UUID.randomUUID ().toString ();
    }

    @SuppressWarnings("unchecked")
    public static <T extends Persistable> T modify(Persistable persistable, Class<T> clatt) throws WTException {
	Assert.notNull (persistable,"Persistable is required");
	Assert.notNull (clatt,"Class<? extends Persistable> is required");
	return (T) PersistenceHelper.manager.modify (persistable);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Persistable> T refresh(Persistable persistable, Class<T> clatt) throws WTException {
	Assert.notNull (persistable,"Persistable is required");
	Assert.notNull (clatt,"Class<? extends Persistable> is required");
	return (T) PersistenceHelper.manager.refresh (persistable);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Persistable> T save(Persistable persistable, Class<T> clatt) throws WTException {
	Assert.notNull (persistable,"Persistable is required");
	Assert.notNull (clatt,"Class<? extends Persistable> is required");
	return (T) PersistenceHelper.manager.save (persistable);
    }

    public static String toLocalizedMessage(String prompt, Object... objects) {
	return new WTMessage (ServerUtils.EXCEPTION_RB,prompt,objects).getLocalizedMessage ();
    }

    public static void zip(File repository, File zipFile) {
	Assert.notNull (repository,"Zip directory is required");
	Assert.notNull (zipFile,"Zip file is required");

	ZipOutputStream zos = null;
	try {
	    zos = new ZipOutputStream (new FileOutputStream (zipFile));
	    compress (repository,zos,repository.getName ());
	}
	catch(Exception e) {
	    e.printStackTrace ();
	}
	finally {
	    try {
		if (zos != null) {
		    zos.close ();
		}
		FileUtils.deleteDirectory (repository);
	    }
	    catch(IOException e) {
		e.printStackTrace ();
	    }
	}
    }

}
