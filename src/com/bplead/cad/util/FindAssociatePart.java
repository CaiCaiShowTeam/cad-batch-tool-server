/**
 * FindAssociatePart.java
 * 2018年10月15日
 */
package com.bplead.cad.util;

import com.bplead.cad.bean.io.Document;

import wt.util.WTException;

/**
 * 2018年10月15日上午10:56:44
 */
public interface FindAssociatePart {
    
    public Object[] getAssociatePart(Document document) throws WTException;
    
    public Object[] getAssociatePart(String partNumber,Document document) throws WTException;
}
