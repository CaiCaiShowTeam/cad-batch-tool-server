package com.bplead.cad.util;

import java.util.ArrayList;
import java.util.List;

import com.bplead.cad.constant.CustomPrompt;

import priv.lee.cad.util.Assert;
import wt.doc.WTDocument;
import wt.fc.PersistenceHelper;
import wt.fc.PersistenceServerHelper;
import wt.fc.QueryResult;
import wt.fc.collections.WTHashSet;
import wt.fc.collections.WTSet;
import wt.method.RemoteAccess;
import wt.part.WTPart;
import wt.part.WTPartDescribeLink;
import wt.util.WTException;
import wt.util.WTPropertyVetoException;
import wt.vc.config.LatestConfigSpec;

public class PartDocumentUtils implements RemoteAccess {

	public static List<WTPart> findDescribedParts(WTDocument doc) {
		List<WTPart> list = new ArrayList<WTPart>();
		try {
			LatestConfigSpec config = new LatestConfigSpec();
			QueryResult result = PersistenceHelper.manager.navigate(doc, WTPartDescribeLink.DESCRIBES_ROLE,
					WTPartDescribeLink.class, true);
			result = config.process(result);
			while (result.hasMoreElements()) {
				WTPart part = (WTPart) result.nextElement();
				list.add(part);
			}
		} catch (WTException e) {
			e.printStackTrace();
		}
		return list;
	}

	public static WTSet findDescribeLinks(WTPart part, WTDocument doc) {
		WTSet set = new WTHashSet();
		try {
			QueryResult result = PersistenceHelper.manager.find(WTPartDescribeLink.class, part,
					WTPartDescribeLink.DESCRIBES_ROLE, doc);
			while (result.hasMoreElements()) {
				set.add(result.nextElement());
			}
		} catch (WTException e) {
			e.printStackTrace();
		}
		return set;
	}

	public static boolean saveOrUpdateDescribeLinks(WTDocument doc, List<WTPart> parts)
			throws WTException, WTPropertyVetoException {
		Assert.notNull(doc, CommonUtils.toLocalizedMessage(CustomPrompt.MISS_CONFIGURATION, WTDocument.class));
		if (parts == null) {
			return true;
		}

		// ~ delete old WTPartDescribeLink
		List<WTPart> currentDescribedParts = findDescribedParts(doc);
		if (currentDescribedParts != null && !currentDescribedParts.isEmpty()) {
			currentDescribedParts.removeAll(parts);
			for (WTPart part : currentDescribedParts) {
				if (!part.isLatestIteration()) {
					continue;
				}

				WTPart copy = CommonUtils.checkout(part, null, WTPart.class);

				PersistenceHelper.manager.delete(findDescribeLinks(copy, doc));

				CommonUtils.checkin(copy, null, WTPart.class);
			}
		}

		// ~ insert new WTPartDescribeLink
		for (WTPart part : parts) {
			if (findDescribeLinks(part, doc).isEmpty()) {
				WTPart copy = CommonUtils.checkout(part, null, WTPart.class);

				WTPartDescribeLink wtpartdescribelink = WTPartDescribeLink.newWTPartDescribeLink(copy, doc);
				PersistenceServerHelper.manager.insert(wtpartdescribelink);

				CommonUtils.checkin(copy, null, WTPart.class);
			}
		}
		return true;
	}
}
