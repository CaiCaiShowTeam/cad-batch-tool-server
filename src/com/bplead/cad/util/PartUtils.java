package com.bplead.cad.util;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;

import com.bplead.cad.bean.io.CAD;
import com.bplead.cad.bean.io.CAPP;
import com.bplead.cad.bean.io.MPMLine;
import com.bplead.cad.bean.io.MPMPart;
import com.bplead.cad.constant.CustomPrompt;

import priv.lee.cad.util.Assert;
import priv.lee.cad.util.StringUtils;
import wt.fc.Persistable;
import wt.fc.PersistenceHelper;
import wt.fc.QueryResult;
import wt.iba.definition.StringDefinition;
import wt.iba.value.StringValue;
import wt.method.RemoteAccess;
import wt.part.WTPart;
import wt.part.WTPartMaster;
import wt.query.ArrayExpression;
import wt.query.ConstantExpression;
import wt.query.OrderBy;
import wt.query.QueryException;
import wt.query.QuerySpec;
import wt.query.SearchCondition;
import wt.query.SubSelectExpression;
import wt.query.TableColumn;
import wt.session.SessionServerHelper;
import wt.util.WTException;
import wt.util.WTPropertyVetoException;
import wt.vc.views.View;
import wt.vc.wip.WorkInProgressState;

public class PartUtils implements RemoteAccess {

	private static final String CHECKOUT_INFO = "STATECHECKOUTINFO";
	public static final String DRAWING_NUM = "TZH";
	private static DecimalFormat format = new DecimalFormat("000");
	private static final String IDA3A6 = "ida3a6";
	private static final String INSTANCE_PREFIX = "(";
	private static final String INSTANCE_SUFFIX = ")";
	private static final String LATEST_ITERATION = "LATESTITERATIONINFO";
	private static final Logger logger = Logger.getLogger(PartUtils.class);
	private static final String MANU_PAGE_SEP = ";";
	private static final String MANUFACTOR_PAGE = "gyssym";
	private static final String MASTER_REFERENCE = "IDA3MASTERREFERENCE";
	private static final String NAME = "NAME";
	private static final String NUMBER = "WTPARTNUMBER";
	private static final String ONE_OFF_VERSION = "ONEOFFVERSIONIDA2ONEOFFVERSI";
	private static final String OPERATION_SEP = "-";
	private static final String PRIMARY_KEY = "IDA2A2";
	private static final String SEPERATOR = "~";
	private static final String VALUE2 = "value2";
	private static final String VERSION = "VERSIONIDA2VERSIONINFO";
	private static final String VIEW = "IDA3VIEW";
	public static final String VIEW_M = "M";

	private static SubSelectExpression buildNumberSubQuery(String number) throws QueryException {
		QuerySpec query = CommonUtils.getAdvancedQuery();

		int index = query.addClassList(WTPartMaster.class, false);

		String[] alias = new String[1];
		alias[0] = query.getFromClause().getAliasAt(index);

		TableColumn primaryKeyColumn = new TableColumn(alias[0], PRIMARY_KEY);
		TableColumn numberColumn = new TableColumn(alias[0], NUMBER);

		query.appendSelect(primaryKeyColumn, false);

		query.appendWhere(
				new SearchCondition(numberColumn, SearchCondition.EQUAL, new ConstantExpression(number.toUpperCase())),
				new int[] { 0 });
		logger.debug("query:" + query.toString());
		return new SubSelectExpression(query);
	}

	private static SubSelectExpression buildViewSubQuery() throws QueryException {
		QuerySpec query = CommonUtils.getAdvancedQuery();

		int index = query.addClassList(View.class, false);

		String[] alias = new String[1];
		alias[0] = query.getFromClause().getAliasAt(index);

		TableColumn primaryKeyColumn = new TableColumn(alias[0], PRIMARY_KEY);
		TableColumn nameColumn = new TableColumn(alias[0], NAME);

		query.appendSelect(primaryKeyColumn, false);

		query.appendWhere(new SearchCondition(nameColumn, SearchCondition.EQUAL, new ConstantExpression(VIEW_M)),
				new int[] { 0 });
		logger.debug("query:" + query.toString());
		return new SubSelectExpression(query);
	}

	private static String getManufactorPage(List<MPMPart> mpmParts) {
		if (mpmParts == null) {
			return null;
		}

		StringBuilder builder = new StringBuilder();
		for (MPMPart part : mpmParts) {
			List<MPMLine> lines = part.getDetail();
			for (MPMLine line : lines) {
				builder.append(line.getOperationNum()).append(OPERATION_SEP).append(line.getPage())
						.append(MANU_PAGE_SEP);
			}
		}
		return builder.toString();
	}

	public static WTPart getWTPart(String number) {
		Assert.hasText(number, CommonUtils.toLocalizedMessage(CustomPrompt.MISS_WTPART_NUMBER));

		boolean enforced = SessionServerHelper.manager.setAccessEnforced(false);
		try {
			QuerySpec query = CommonUtils.getAdvancedQuery();

			int partIndex = query.addClassList(WTPart.class, true);

			String[] alias = new String[1];
			alias[0] = query.getFromClause().getAliasAt(partIndex);

			TableColumn referenceColumn = new TableColumn(alias[0], MASTER_REFERENCE);
			TableColumn latestColumn = new TableColumn(alias[0], LATEST_ITERATION);
			TableColumn viewColumn = new TableColumn(alias[0], VIEW);
			TableColumn checkoutColumn = new TableColumn(alias[0], CHECKOUT_INFO);
			TableColumn oneOffColumn = new TableColumn(alias[0], ONE_OFF_VERSION);
			TableColumn versionColumn = new TableColumn(alias[0], VERSION);

			query.appendWhere(new SearchCondition(referenceColumn, SearchCondition.IN, buildNumberSubQuery(number)),
					new int[] { 0 });

			query.appendAnd();

			query.appendWhere(new SearchCondition(latestColumn, SearchCondition.IS_TRUE), new int[] { 0 });

			query.appendAnd();

			query.appendWhere(new SearchCondition(viewColumn, SearchCondition.IN, buildViewSubQuery()),
					new int[] { 0 });

			query.appendAnd();

			query.appendWhere(new SearchCondition(checkoutColumn, SearchCondition.NOT_EQUAL,
					new ConstantExpression(WorkInProgressState.WORKING)), new int[] { 0 });

			query.appendAnd();

			query.appendWhere(new SearchCondition(oneOffColumn, SearchCondition.IS_NULL), new int[] { 0 });

			query.appendOrderBy(new OrderBy(versionColumn, true), new int[] { 0 });
			logger.debug("query:" + query.toString());

			QueryResult result = PersistenceHelper.manager.find(query);
			if (result.hasMoreElements()) {
				return (WTPart) (((Object[]) result.nextElement())[0]);
			}
		} catch (WTException e) {
			e.printStackTrace();
		} finally {
			SessionServerHelper.manager.setAccessEnforced(enforced);
		}
		return null;
	}

	public static List<WTPart> search(CAD cad) {
		Assert.notNull(cad, CommonUtils.toLocalizedMessage(CustomPrompt.MISS_CONFIGURATION, CAD.class));

		List<WTPart> parts = null;
		if (cad.getDetailNum().equals(cad.getNumber())) {
			parts = Arrays.asList(getWTPart(cad.getJdeNum()));
		} else {
			parts = searchInstances(cad.getDetailNum());
		}
		return parts;
	}

	public static List<WTPart> searchInstances(String detailNum) {
		List<String> instanceNumbers = toInstanceNums(detailNum);
		Assert.notEmpty(instanceNumbers, CommonUtils.toLocalizedMessage(CustomPrompt.INSTANCE_NUMBER_ERROR));

		List<WTPart> parts = new ArrayList<WTPart>();
		boolean enforced = SessionServerHelper.manager.setAccessEnforced(false);
		try {
			QuerySpec query = CommonUtils.getAdvancedQuery();

			int valueIndex = query.addClassList(StringValue.class, true);
			int definitionIndex = query.addClassList(StringDefinition.class, false);

			String[] alias = new String[2];
			alias[0] = query.getFromClause().getAliasAt(valueIndex);
			alias[1] = query.getFromClause().getAliasAt(definitionIndex);

			TableColumn ida3a6Column = new TableColumn(alias[0], IDA3A6);
			TableColumn value2Column = new TableColumn(alias[0], VALUE2);
			TableColumn primaryKeyColumn = new TableColumn(alias[1], PRIMARY_KEY);
			TableColumn nameColumn = new TableColumn(alias[1], NAME);

			query.appendWhere(
					new SearchCondition(nameColumn, SearchCondition.EQUAL, new ConstantExpression(DRAWING_NUM)),
					new int[] { 1 });

			query.appendAnd();

			query.appendWhere(new SearchCondition(primaryKeyColumn, SearchCondition.EQUAL, ida3a6Column),
					new int[] { 0 });

			query.appendAnd();

			query.appendWhere(new SearchCondition(value2Column, SearchCondition.IN,
					new ArrayExpression(instanceNumbers.toArray())), new int[] { 0 });
			logger.debug("query:" + query.toString());

			QueryResult result = PersistenceHelper.manager.find(query);
			while (result.hasMoreElements()) {
				StringValue value = (StringValue) (((Object[]) result.nextElement())[0]);
				Persistable persistable = value.getIBAHolderReference().getObject();
				if (!(persistable instanceof WTPart)) {
					continue;
				}

				WTPart part = getWTPart(((WTPart) persistable).getNumber());
				if (!parts.contains(part)) {
					parts.add(part);
				}
			}
		} catch (WTException e) {
			e.printStackTrace();
		} finally {
			SessionServerHelper.manager.setAccessEnforced(enforced);
		}
		return parts;
	}

	public static List<String> toInstanceNums(String detailNum) {
		Assert.hasText(detailNum, CommonUtils.toLocalizedMessage(CustomPrompt.MISS_CONFIGURATION, String.class));

		int prefixIndex = detailNum.indexOf(INSTANCE_PREFIX);
		int suffixIndex = detailNum.indexOf(INSTANCE_SUFFIX);
		List<String> instanceNumbers = new ArrayList<String>();
		if (prefixIndex > 0 && suffixIndex > prefixIndex) {
			String prefix = detailNum.substring(0, prefixIndex);
			String suffix = detailNum.substring(suffixIndex + 1, detailNum.length());

			int seperatorIndex = detailNum.indexOf(SEPERATOR);
			int start = Integer.parseInt(detailNum.substring(prefixIndex + 1, seperatorIndex));
			int end = Integer.parseInt(detailNum.substring(seperatorIndex + 1, suffixIndex));
			logger.debug(prefix + "," + start + "," + end + "," + suffix);
			for (int i = start; i <= end; i++) {
				StringBuilder number = new StringBuilder(prefix).append(format.format(i)).append(suffix);
				instanceNumbers.add(number.toString());
			}
		} else {
			instanceNumbers.add(detailNum);
		}
		return instanceNumbers;
	}

	public static List<WTPart> update(CAPP capp, boolean updated) throws WTPropertyVetoException {
		List<MPMPart> mpmParts = capp.getMpmParts();
		Assert.notEmpty(mpmParts, CommonUtils.toLocalizedMessage(CustomPrompt.MISS_CONFIGURATION, List.class));

		List<WTPart> list = new ArrayList<WTPart>();
		for (MPMPart mpmPart : mpmParts) {
			String jdeNum = mpmPart.getJdeNum();
			Assert.notNull(jdeNum, CommonUtils.toLocalizedMessage(CustomPrompt.MISS_JDENUM));

			WTPart part = getWTPart(jdeNum);
			Assert.notNull(part, CommonUtils.toLocalizedMessage(CustomPrompt.WTPART_NOT_EXIST, jdeNum));

			if (list.contains(part)) {
				continue;
			}

			if (updated) {
				part = updateIBAs(part, capp);
			}
			list.add(part);
		}
		return list;
	}

	private static WTPart updateIBAs(WTPart part, CAPP capp) throws WTPropertyVetoException {
		String value = getManufactorPage(capp.getMpmParts());
		if (StringUtils.hasText(value)) {
			IBAUtils utils = new IBAUtils(part);
			utils.setIBAValue(MANUFACTOR_PAGE, value);
			part = utils.updateAttributeContainer(part, WTPart.class);
		}
		return part;
	}
}
