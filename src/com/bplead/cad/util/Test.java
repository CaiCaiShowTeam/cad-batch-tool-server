package com.bplead.cad.util;

import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;

import wt.method.RemoteAccess;
import wt.method.RemoteMethodServer;
import wt.part.WTPart;
import wt.util.WTException;

public class Test implements RemoteAccess{
	public static final String DRAWING_NUM = "TZH";
	public static final String VIEW_M = "M";
	public static final String MATERIALMODEL = "CSR_XINGHAOGUIGE";
	public static final String SIGLETONWEIGHT = "CSR_ZHONGLIANG";
	public static final String TOTALWEIGHT = "CSR_ZONGZHONG";
	public static final String DESCIPTION = "CSR_BEIZHU";

	@SuppressWarnings({ "rawtypes" })
	public static void main(String[] args) throws RemoteException, InvocationTargetException {
		String name = "";
		if(args.length > 0) {
			name = args[0];
		}
		RemoteMethodServer rms = RemoteMethodServer.getDefault();
		Class[] cls = {String.class};
		Object[] obj = {name};
		rms.invoke("process", Test.class.getName(), null, cls, obj);

	}
	
	public static void process(String name) throws WTException {
		System.out.println("111--->");
		WTPart part = CADHelper.getLatestWTPart("C0M0000", "Design", null);
		System.out.println("part--->"+part);
	}
	
}
