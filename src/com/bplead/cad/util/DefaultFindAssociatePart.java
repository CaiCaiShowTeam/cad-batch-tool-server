/**
 * DefaultFindAssociatePart.java
 * 2018年10月15日
 */
package com.bplead.cad.util;

import org.apache.log4j.Logger;

import com.bplead.cad.bean.io.Document;

import wt.log4j.LogR;
import wt.part.WTPart;
import wt.util.WTException;

/**
 * 2018年10月15日上午10:58:41
 */
public class DefaultFindAssociatePart implements FindAssociatePart {
    
    private static Logger logger = LogR.getLogger (DefaultFindAssociatePart.class.getName ());

    /* (non-Javadoc)
     * @see com.bplead.cad.util.FindAssociatePart#getAssociatePart(com.bplead.cad.bean.io.Document)
     */
    @Override
    public WTPart getAssociatePart(Document document) throws WTException {
	String partNumber = CADHelper.removeExtension (document.getNumber ());
	if (logger.isDebugEnabled ()) {
	    logger.debug ("partNumber is -> " + partNumber);
	}
	WTPart part = PartUtils.getWTPart (partNumber);
	//if part is null create
	if (part == null) {
	    part = CADHelper.createPart (document);
	} 
	return part;
    }

}
