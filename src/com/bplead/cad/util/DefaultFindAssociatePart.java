/**
 * DefaultFindAssociatePart.java 2018年10月15日
 */
package com.bplead.cad.util;

import org.apache.log4j.Logger;

import com.bplead.cad.bean.io.CadDocument;
import com.bplead.cad.bean.io.CadStatus;
import com.bplead.cad.bean.io.Document;
import com.bplead.cad.bean.io.PartCategory;

import wt.log4j.LogR;
import wt.part.WTPart;
import wt.util.WTException;

/**
 * 2018年10月15日上午10:58:41
 */
public class DefaultFindAssociatePart implements FindAssociatePart {

    private static Logger logger = LogR.getLogger (DefaultFindAssociatePart.class.getName ());

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.bplead.cad.util.FindAssociatePart#getAssociatePart(com.bplead.cad.
     * bean.io.Document)
     */
    @Override
    public Object[] getAssociatePart(Document document) throws WTException {
	CadStatus cadStatus = document.getCadStatus ();
	String epmNumber = "";
	if (cadStatus == CadStatus.NOT_EXIST) {
	    CadDocument cadDocument = (CadDocument) document.getObject ();
	    epmNumber = cadDocument.getNumber ();
	} else {
	    epmNumber = document.getNumber ();
	}
	if (logger.isDebugEnabled ()) {
	    logger.debug ("getAssociatePart epmNumber is -> " + epmNumber);
	}
	WTPart part = CADHelper.getLatestWTPart (epmNumber,"Design",null); 
	// if part is null create
	Boolean exist = false;
	if (part == null) {
	    if (logger.isInfoEnabled ()) {
		logger.info ("系统中没有查询到编号为[" + epmNumber + "]的部件.");
		logger.info ("创建epm文档关联部件开始...");
	    }
	    PartCategory category = CADHelper.getPartCategory (document);
	    if (logger.isDebugEnabled ()) {
		logger.debug ("category is -> " + category);
	    }
	    //如果关联部件不存在并且是外购件,则报错
	    if (category == PartCategory.BUY) {
		throw new WTException ("外购件[" + epmNumber + "]在系统中不存在.");
	    }
	    part = CADHelper.createPart (document);
	    if (logger.isInfoEnabled ()) {
		logger.info ("创建epm文档关联部件结束...");
	    }
	} else {
	    PartCategory category = CADHelper.getPartCategory (document);
	    PartCategory category1 = CADHelper.getPartCategory (part); 
	    //如果检入的是外购件,但存在的是自制件则报错
	    if (category == PartCategory.BUY && category1 == PartCategory.MAKE) {
		throw new WTException ("系统中存在相同编号的自制件[" + epmNumber + "]");
	    }
	    exist = true;
	}
	return new Object [] {part,exist};
    }

    /* (non-Javadoc)
     * @see com.bplead.cad.util.FindAssociatePart#getAssociatePart(java.lang.String)
     */
    @Override
    public Object [] getAssociatePart(String partNumber,Document document) throws WTException {
	WTPart part = CADHelper.getLatestWTPart (partNumber,"Design",null); 
	// if part is null create
	Boolean exist = false;
	if (part == null) {
	    if (logger.isInfoEnabled ()) {
		logger.info ("系统中没有查询到编号为[" + partNumber + "]的部件.");
		logger.info ("创建epm文档关联部件开始...");
	    }
	    PartCategory category = CADHelper.getPartCategory (document);
	    if (logger.isDebugEnabled ()) {
		logger.debug ("category is -> " + category);
	    }
	    //如果关联部件不存在并且是外购件,则报错
	    if (category == PartCategory.BUY) {
		throw new WTException ("外购件[" + partNumber + "]在系统中不存在.");
	    }
	    part = CADHelper.createPart (document);
	    if (logger.isInfoEnabled ()) {
		logger.info ("创建epm文档关联部件结束...");
	    }
	} else {
	    PartCategory category = CADHelper.getPartCategory (document);
	    PartCategory category1 = CADHelper.getPartCategory (part); 
	    //如果检入的是外购件,但存在的是自制件则报错
	    if (category == PartCategory.BUY && category1 == PartCategory.MAKE) {
		throw new WTException ("系统中存在相同编号的自制件[" + partNumber + "]");
	    }
	    exist = true;
	}
	return new Object [] {part,exist};
    }

}
