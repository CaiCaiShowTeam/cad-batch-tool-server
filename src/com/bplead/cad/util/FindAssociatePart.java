/**
 * FindAssociatePart.java
 * 2018年10月15日
 */
package com.bplead.cad.util;

import com.bplead.cad.bean.io.Document;

import wt.part.WTPart;
import wt.util.WTException;

/**
 * @author zjw
 * 2018年10月15日上午10:56:44
 */
public interface FindAssociatePart {
    public WTPart getAssociatePart(Document document) throws WTException;
}
