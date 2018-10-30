/**
 * DefaultFindAssociatePart.java 2018年10月15日
 */
package com.bplead.cad.util;

import org.apache.log4j.Logger;

import com.bplead.cad.bean.io.CadDocument;
import com.bplead.cad.bean.io.CadStatus;
import com.bplead.cad.bean.io.Document;

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
    public WTPart getAssociatePart(Document document) throws WTException {
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
	String partNumber = CADHelper.removeExtension (epmNumber);
	if (logger.isDebugEnabled ()) {
	    logger.debug ("partNumber is -> " + partNumber);
	}
	WTPart part = PartUtils.getWTPart (partNumber);
	// if part is null create
	if (part == null) {
	    if (logger.isInfoEnabled ()) {
		logger.info ("系统中没有查询到编号为[" + partNumber + "]的部件.");
		logger.info ("创建epm文档关联部件开始...");
	    }
	    part = CADHelper.createPart (document);
	    if (logger.isInfoEnabled ()) {
		logger.info ("创建epm文档关联部件结束...");
	    }
	}
	return part;
    }

}
