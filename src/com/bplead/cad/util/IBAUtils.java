package com.bplead.cad.util;

import java.sql.Timestamp;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import java.util.Vector;

import com.ptc.core.command.common.bean.entity.NewEntityCommand;
import com.ptc.core.foundation.type.server.impl.SoftAttributesHelper;
import com.ptc.core.meta.common.AnalogSet;
import com.ptc.core.meta.common.AttributeIdentifier;
import com.ptc.core.meta.common.AttributeTypeIdentifier;
import com.ptc.core.meta.common.ConstraintIdentifier;
import com.ptc.core.meta.common.DataSet;
import com.ptc.core.meta.common.DataTypesUtility;
import com.ptc.core.meta.common.DefinitionIdentifier;
import com.ptc.core.meta.common.DiscreteSet;
import com.ptc.core.meta.common.IdentifierFactory;
import com.ptc.core.meta.common.OperationIdentifier;
import com.ptc.core.meta.common.TypeIdentifier;
import com.ptc.core.meta.common.TypeInstanceIdentifier;
import com.ptc.core.meta.common.WildcardSet;
import com.ptc.core.meta.container.common.AttributeContainerSpec;
import com.ptc.core.meta.container.common.AttributeTypeSummary;
import com.ptc.core.meta.container.common.ConstraintContainer;
import com.ptc.core.meta.container.common.ConstraintData;
import com.ptc.core.meta.container.common.ConstraintException;
import com.ptc.core.meta.container.common.impl.DefaultConstraintValidator;
import com.ptc.core.meta.server.TypeIdentifierUtility;
import com.ptc.core.meta.type.common.TypeInstance;
import com.ptc.core.meta.type.server.TypeInstanceUtility;

import wt.fc.Persistable;
import wt.fc.PersistenceServerHelper;
import wt.iba.definition.DefinitionLoader;
import wt.iba.definition.litedefinition.AbstractAttributeDefinizerView;
import wt.iba.definition.litedefinition.AttributeDefDefaultView;
import wt.iba.definition.litedefinition.AttributeDefNodeView;
import wt.iba.definition.litedefinition.BooleanDefView;
import wt.iba.definition.litedefinition.FloatDefView;
import wt.iba.definition.litedefinition.IntegerDefView;
import wt.iba.definition.litedefinition.RatioDefView;
import wt.iba.definition.litedefinition.ReferenceDefView;
import wt.iba.definition.litedefinition.StringDefView;
import wt.iba.definition.litedefinition.TimestampDefView;
import wt.iba.definition.litedefinition.URLDefView;
import wt.iba.definition.litedefinition.UnitDefView;
import wt.iba.definition.service.IBADefinitionHelper;
import wt.iba.value.AttributeContainer;
import wt.iba.value.DefaultAttributeContainer;
import wt.iba.value.IBAHolder;
import wt.iba.value.IBAValueUtility;
import wt.iba.value.litevalue.AbstractValueView;
import wt.iba.value.service.IBAValueDBService;
import wt.iba.value.service.IBAValueHelper;
import wt.iba.value.service.LoadValue;
import wt.services.applicationcontext.implementation.DefaultServiceProvider;
import wt.session.SessionHelper;
import wt.util.WTContext;
import wt.util.WTException;
import wt.util.WTPropertyVetoException;
import wt.util.WTStandardDateFormat;
import wt.util.range.Range;

public class IBAUtils {

	public static final String IBA_DATATYPE = "IBA_DATATYPE";
	public static final String IBA_EDITABLE = "IBA_EDITABLE";
	public static final String IBA_IDENTIFIER = "IBA_IDENTIFIER";
	public static final String IBA_LABEL = "IBA_LABEL";
	public static final String IBA_NAME = "IBA_NAME";
	public static final String IBA_OPTIONS_VECTOR = "IBA_OPTIONS_VECTOR";
	public static final String IBA_REQUIRED = "IBA_REQUIRED";
	public static final String IBA_STRING_LENGTH_MAX = "IBA_STRING_LENGTH_MAX";
	public static final String IBA_STRING_LENGTH_MIN = "IBA_STRING_LENGTH_MIN";
	public static final String IBA_VALUE = "IBA_VALUE";

	static Object convertStringToIBAValue(String strVal, String dataType, Locale locale, TimeZone timezone)
			throws WTException {
		Object obj = null;
		if (dataType.equals("java.lang.Long"))
			try {
				obj = Long.valueOf(strVal);
			} catch (Exception exception) {
				Object aobj1[] = { strVal };
				throw new WTException("com.ptc.core.HTMLtemplateutil.server.processors.processorsResource", "58",
						aobj1);
			}
		else if (dataType.equals("com.ptc.core.meta.common.FloatingPoint")) {
			try {
				obj = DataTypesUtility.toFloatingPoint(strVal, locale);
			} catch (Exception exception1) {
				Object aobj3[] = { strVal };
				throw new WTException("com.ptc.core.HTMLtemplateutil.server.processors.processorsResource", "59",
						aobj3);
			}
		} else if (dataType.equals("java.lang.Boolean")) {
			obj = Boolean.valueOf(strVal);
		} else if (dataType.equals("java.sql.Timestamp")) {
			try {
				Date date = null;
				try {
					date = WTStandardDateFormat.parse(strVal, 3, locale, timezone);
				} catch (ParseException parseexception) {
					try {
						date = WTStandardDateFormat.parse(strVal, 25, locale, timezone);
					} catch (ParseException parseexception1) {
						date = WTStandardDateFormat.parse(strVal, 26, locale, timezone);
					}
				}
				obj = new Timestamp(date.getTime());
			} catch (ParseException parseexception) {
				Object aobj5[] = { strVal };
				throw new WTException("com.ptc.core.HTMLtemplateutil.server.processors.processorsResource", "60",
						aobj5);
			}
		} else {
			obj = new String(strVal);
		}
		return obj;
	}

	@SuppressWarnings({ "rawtypes", "deprecation", "unchecked" })
	static TypeInstance getIBAValuesInternal(Object obj, ArrayList ibaList, HashMap ibaMap, boolean returnOpts)
			throws WTException {
		TypeInstanceIdentifier tii = null;
		Locale locale = WTContext.getContext().getLocale();
		boolean forTypedObj = false;

		if (obj instanceof IBAHolder) {
			tii = TypeIdentifierUtility.getTypeInstanceIdentifier(obj);
			forTypedObj = true;
		} else {
			IdentifierFactory idFactory = (IdentifierFactory) DefaultServiceProvider
					.getService(com.ptc.core.meta.common.IdentifierFactory.class, "default");
			TypeIdentifier ti = (TypeIdentifier) idFactory.get((String) obj);
			tii = ti.newTypeInstanceIdentifier();
		}

		TypeInstance typeInstance = null;
		try {
			typeInstance = SoftAttributesHelper.getSoftSchemaTypeInstance(tii, null, locale);
		} catch (UnsupportedOperationException unsupportedoperationexception) {
			throw new WTException(unsupportedoperationexception, "SoftAttributesHelper.getSoftSchemaTypeInstance(): "
					+ "Exception encountered when trying to create a type instance");
		}

		if (forTypedObj) {
			TypeInstanceUtility.populateMissingTypeContent(typeInstance, null);
		}

		AttributeIdentifier[] ais = typeInstance.getAttributeIdentifiers();
		for (int i = 0; ais != null && i < ais.length; i++) {
			DefinitionIdentifier di = ais[i].getDefinitionIdentifier();
			AttributeTypeIdentifier ati = (AttributeTypeIdentifier) di;
			AttributeTypeSummary ats = typeInstance.getAttributeTypeSummary(ati);

			String ibaIdentifier = ais[i].toExternalForm();
			String name = ati.getAttributeName();
			ati.getWithTailContext();

			String value = String.valueOf(typeInstance.get(ais[i]));
			String dataType = ats.getDataType();
			String label = ats.getLabel();
			Boolean required = ats.isRequired() ? new Boolean(true) : null;
			Boolean editable = ats.isEditable() ? new Boolean(true) : null;

			int min = ats.getMinStringLength();
			int max = ats.getMaxStringLength();
			Integer minStringLength = min == 0 ? null : new Integer(min);
			Integer maxStringLength = max == 0 ? null : new Integer(max);

			HashMap ibaInfo = new HashMap();
			ibaInfo.put(IBA_IDENTIFIER, ibaIdentifier);
			ibaInfo.put(IBA_NAME, name);
			ibaInfo.put(IBA_VALUE, value);
			ibaInfo.put(IBA_LABEL, label);
			ibaInfo.put(IBA_DATATYPE, dataType);
			ibaInfo.put(IBA_REQUIRED, required);
			ibaInfo.put(IBA_EDITABLE, editable);
			ibaInfo.put(IBA_STRING_LENGTH_MIN, minStringLength);
			ibaInfo.put(IBA_STRING_LENGTH_MAX, maxStringLength);

			if (returnOpts) {
				Vector options = null;
				DataSet dsVal = ats.getLegalValueSet();
				if (dsVal != null && dsVal instanceof DiscreteSet) {
					Object[] eles = ((DiscreteSet) dsVal).getElements();
					options = new Vector();
					for (int j = 0; eles != null && j < eles.length; j++) {
						options.add(String.valueOf(eles[j]));
					}
				}
				ibaInfo.put(IBA_OPTIONS_VECTOR, options);
			}

			if (ibaList != null) {
				ibaList.add(ibaInfo);
			}

			if (ibaMap != null) {
				ibaMap.put(name, ibaInfo);
			}
		}
		return typeInstance;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static WTException interpretConstraintViolationException(ConstraintException constraintexception,
			Locale locale) throws WTException {
		AttributeIdentifier attributeidentifier = constraintexception.getAttributeIdentifier();
		AttributeTypeIdentifier attributetypeidentifier = (AttributeTypeIdentifier) attributeidentifier
				.getDefinitionIdentifier();
		AttributeContainerSpec attributecontainerspec = new AttributeContainerSpec();
		attributecontainerspec.putEntry(attributetypeidentifier, true, true);
		NewEntityCommand newentitycommand = new NewEntityCommand();
		try {
			((NewEntityCommand) newentitycommand).setIdentifier(attributetypeidentifier.getContext());
			newentitycommand.setFilter(attributecontainerspec);
			newentitycommand.setLocale(locale);
		} catch (WTPropertyVetoException wtpropertyvetoexception) {
			throw new WTException(wtpropertyvetoexception);
		}
		newentitycommand.execute();
		TypeInstance typeinstance = newentitycommand.getResult();
		AttributeTypeSummary attributetypesummary = typeinstance
				.getAttributeTypeSummary((AttributeTypeIdentifier) attributeidentifier.getDefinitionIdentifier());
		String s = attributetypesummary.getLabel();
		ConstraintIdentifier constraintidentifier = constraintexception.getConstraintIdentifier();
		String s1 = constraintidentifier.getEnforcementRuleClassname();
		ConstraintData constraintdata = constraintexception.getConstraintData();
		String s3 = "com.ptc.core.HTMLtemplateutil.server.processors.processorsResource";
		String s4 = null;
		java.io.Serializable serializable = constraintdata.getEnforcementRuleData();
		ArrayList arraylist = new ArrayList();
		arraylist.add(s);
		if (s1.equals("com.ptc.core.meta.container.common.impl.RangeConstraint")) {
			if (serializable instanceof AnalogSet) {
				Range range = ((AnalogSet) serializable).getBoundingRange();
				if (range.hasLowerBound() && range.hasUpperBound()) {
					arraylist.add(range.getLowerBoundValue());
					arraylist.add(range.getUpperBoundValue());
					s4 = "72";
				} else if (range.hasLowerBound()) {
					arraylist.add(range.getLowerBoundValue());
					s4 = "73";
				} else if (range.hasUpperBound()) {
					arraylist.add(range.getUpperBoundValue());
					s4 = "74";
				}
			} else {
				s4 = "75";
			}
		} else if (s1.equals("com.ptc.core.meta.container.common.impl.ImmutableConstraint"))
			s4 = "78";
		else if (s1.equals("com.ptc.core.meta.container.common.impl.DiscreteSetConstraint")) {
			if (serializable instanceof DiscreteSet) {
				Object aobj[] = ((DiscreteSet) serializable).getElements();
				String s5 = "";
				for (int j = 0; j < aobj.length; j++)
					s5 = s5 + aobj[j].toString() + ",";

				String s7 = s5.substring(0, s5.length() - 1);
				arraylist.add(s7);
				s4 = "83";
			} else {
				s4 = "84";
			}
		} else if (s1.equals("com.ptc.core.meta.container.common.impl.StringLengthConstraint")) {
			if (serializable instanceof AnalogSet) {
				Range range1 = ((AnalogSet) serializable).getBoundingRange();
				if (range1.hasLowerBound() && range1.hasUpperBound()) {
					arraylist.add(range1.getLowerBoundValue());
					arraylist.add(range1.getUpperBoundValue());
					s4 = "79";
				} else if (range1.hasLowerBound()) {
					arraylist.add(range1.getLowerBoundValue());
					s4 = "80";
				} else if (range1.hasUpperBound()) {
					arraylist.add(range1.getUpperBoundValue());
					s4 = "81";
				}
			} else {
				s4 = "82";
			}
		} else if (s1.equals("com.ptc.core.meta.container.common.impl.StringFormatConstraint")) {
			if (serializable instanceof DiscreteSet) {
				Object aobj1[] = ((DiscreteSet) serializable).getElements();
				String s6 = "";
				for (int k = 0; k < aobj1.length; k++)
					s6 = s6 + "\"" + aobj1[k].toString() + "\" or ";

				String s8 = s6.substring(0, s6.length() - 4);
				arraylist.add(s8);
				s4 = "85";
			} else {
				s4 = "84";
			}
		} else if (s1.equals("com.ptc.core.meta.container.common.impl.UpperCaseConstraint")) {
			s4 = "86";
		} else if (s1.equals("com.ptc.core.meta.container.common.impl.ValueRequiredConstraint")) {
			s4 = "77";
		} else if (s1.equals("com.ptc.core.meta.container.common.impl.WildcardConstraint")) {
			if (serializable instanceof WildcardSet) {
				arraylist.add(((WildcardSet) serializable).getValue());
				int i = ((WildcardSet) serializable).getMode();
				if (i == 1) {
					s4 = "87";
					arraylist.add(((WildcardSet) serializable).getValue());
				} else if (i == 2) {
					if (((WildcardSet) serializable).isNegated())
						s4 = "89";
					else
						s4 = "88";
				} else if (i == 3) {
					if (((WildcardSet) serializable).isNegated())
						s4 = "91";
					else
						s4 = "90";
				} else if (i == 4)
					if (((WildcardSet) serializable).isNegated())
						s4 = "93";
					else
						s4 = "92";
			} else {
				s4 = "84";
			}
		} else {
			s4 = "84";
		}
		if (s4 != null) {
			return new WTException(s3, s4, arraylist.toArray());
		}
		return null;
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static boolean setIBAValues2(IBAHolder ibaHolder, Properties ibaValues) throws WTException {
		HashMap ibaMap = new HashMap();
		Locale locale = WTContext.getContext().getLocale();
		TimeZone tzone = WTContext.getContext().getTimeZone();
		TypeInstance ti = getIBAValuesInternal(ibaHolder, null, ibaMap, false);
		IdentifierFactory idFactory = (IdentifierFactory) DefaultServiceProvider
				.getService(com.ptc.core.meta.common.IdentifierFactory.class, "default");

		ArrayList listIBAId = new ArrayList();
		ArrayList listIBATypeId = new ArrayList();
		ArrayList listIBAValue = new ArrayList();
		for (Enumeration en = ibaValues.keys(); en.hasMoreElements();) {
			String iName = (String) en.nextElement();
			String iVal = (String) ibaValues.get(iName);
			if (iVal == null) {
				continue;
			}

			HashMap ibaInfo = (HashMap) ibaMap.get(iName);
			if (ibaInfo == null) {
				continue;
			}

			Boolean required = (Boolean) ibaInfo.get(IBA_REQUIRED);
			if (required != null && required.booleanValue() && iVal.equals("")) {
				throw new WTException("Value of attribute<" + iName + "> can not be null");
			}

			AttributeIdentifier ai = (AttributeIdentifier) idFactory.get((String) ibaInfo.get(IBA_IDENTIFIER));
			DefinitionIdentifier ati = ai.getDefinitionIdentifier();

			String dataType = (String) ibaInfo.get(IBA_DATATYPE);
			Object iv = convertStringToIBAValue(iVal, dataType, locale, tzone);

			listIBAId.add(ai);
			listIBAValue.add(iv);
			listIBATypeId.add(ati);
		}

		HashMap vmap = new HashMap();
		TypeInstanceIdentifier tii = (TypeInstanceIdentifier) ti.getIdentifier();
		for (int i = 0; i < listIBAId.size(); i++) {
			AttributeTypeIdentifier ati = (AttributeTypeIdentifier) listIBATypeId.get(i);
			AttributeIdentifier[] ais = ti.getAttributeIdentifiers(ati);
			if (ais.length > 0) {
				vmap.put(ais[0], ti.get(ais[0]));
				ti.put(ais[0], listIBAValue.get(i));
			} else {
				AttributeIdentifier ai = ati.newAttributeIdentifier(tii);
				vmap.put(ai, null);
				ti.put(ai, listIBAValue.get(i));
			}
		}

		ti.acceptDefaultContent();
		ti.purgeDefaultContent();

		if (tii.isInitialized()) {
			TypeInstanceUtility.populateConstraints(ti,
					OperationIdentifier.newOperationIdentifier("STDOP|com.ptc.windchill.update"));
		} else {
			TypeInstanceUtility.populateConstraints(ti,
					OperationIdentifier.newOperationIdentifier("STDOP|com.ptc.windchill.create"));
		}
		DefaultConstraintValidator dac = DefaultConstraintValidator.getInstance();
		ConstraintContainer cc = ti.getConstraintContainer();

		if (cc != null) {
			AttributeIdentifier ais[] = ti.getAttributeIdentifiers();
			for (int i = 0; i < ais.length; i++) {
				Object ibaVal = ti.get(ais[i]);
				try {
					dac.isValid(ti, cc, ais[i], ibaVal);
				} catch (ConstraintException ce) {
					if ((!ce.getConstraintIdentifier().getEnforcementRuleClassname()
							.equals("com.ptc.core.meta.container.common.impl.DiscreteSetConstraint") || vmap == null
							|| vmap.get(ais[i]) == null
							|| (!(vmap.get(ais[i]) instanceof Comparable)
									|| ((Comparable) ti.get(ais[i])).compareTo((Comparable) vmap.get(ais[i])) != 0)
									&& !vmap.get(ais[i]).equals(ti.get(ais[i])))
							&& !ce.getConstraintIdentifier().getEnforcementRuleClassname()
									.equals("com.ptc.core.meta.container.common.impl.ImmutableConstraint")) {
						WTException wtexception = interpretConstraintViolationException(ce, locale);
						if (wtexception != null)
							throw wtexception;
					}
				}
			}
		}

		TypeInstanceUtility.updateIBAValues((IBAHolder) ibaHolder, ti);
		return ti.isDirty();
	}

	public static boolean updateIBAHolder(IBAHolder ibaholder) throws WTException {
		IBAValueDBService ibavaluedbservice = new IBAValueDBService();
		boolean flag = true;
		try {
			PersistenceServerHelper.manager.update((Persistable) ibaholder);
			AttributeContainer attributecontainer = ibaholder.getAttributeContainer();
			Object obj = ((DefaultAttributeContainer) attributecontainer).getConstraintParameter();
			AttributeContainer attributecontainer1 = ibavaluedbservice.updateAttributeContainer(ibaholder, obj, null,
					null);
			ibaholder.setAttributeContainer(attributecontainer1);
		} catch (WTException wtexception) {
			throw new WTException("IBAUtility.updateIBAHolder() - Couldn't update IBAHolder : " + wtexception);
		}
		return flag;
	}

	private Hashtable<String, Object[]> ibaContainer;
	private Hashtable<String, String> ibaNames;

	@SuppressWarnings("unused")
	private IBAUtils() {
		ibaContainer = new Hashtable<String, Object[]>();
	}

	public IBAUtils(IBAHolder ibaHolder) {
		initializeIBAPart(ibaHolder);
	}

	private AttributeDefDefaultView getAttributeDefinition(String ibaPath) {
		AttributeDefDefaultView ibaDef = null;
		try {
			ibaDef = IBADefinitionHelper.service.getAttributeDefDefaultViewByPath(ibaPath);
			if (ibaDef == null) {
				AbstractAttributeDefinizerView ibaNodeView = DefinitionLoader.getAttributeDefinition(ibaPath);
				if (ibaNodeView != null)
					ibaDef = IBADefinitionHelper.service.getAttributeDefDefaultView((AttributeDefNodeView) ibaNodeView);
			}
		} catch (Exception wte) {
			wte.printStackTrace();
		}
		return ibaDef;
	}

	public String getIBAValue(String name) {
		try {
			return getIBAValue(name, SessionHelper.manager.getLocale());
		} catch (Exception e) {
			return null;
		}
	}

	public String getIBAValue(String name, Locale locale) {
		try {
			AbstractValueView theValue = (AbstractValueView) ((Object[]) ibaContainer.get(name))[1];
			return (IBAValueUtility.getLocalizedIBAValueDisplayString(theValue, locale));
		} catch (Exception e) {
			return null;
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	public Vector getIBAValues(String name) {
		Vector vector = new Vector();
		try {
			if (ibaContainer.get(name) != null) {
				Object[] objs = (Object[]) ibaContainer.get(name);
				for (int i = 1; i < objs.length; i++) {
					AbstractValueView theValue = (AbstractValueView) objs[i];
					vector.addElement(IBAValueUtility.getLocalizedIBAValueDisplayString(theValue,
							SessionHelper.manager.getLocale()));
				}
			}
		} catch (WTException e) {
			e.printStackTrace();
		}
		return vector;
	}

	private void initializeIBAPart(IBAHolder ibaHolder) {
		ibaContainer = new Hashtable<String, Object[]>();
		ibaNames = new Hashtable<String, String>();
		try {
			ibaHolder = IBAValueHelper.service.refreshAttributeContainer(ibaHolder, null,
					SessionHelper.manager.getLocale(), null);
			DefaultAttributeContainer theContainer = (DefaultAttributeContainer) ibaHolder.getAttributeContainer();
			if (theContainer != null) {
				AttributeDefDefaultView[] theAtts = theContainer.getAttributeDefinitions();
				for (int i = 0; i < theAtts.length; i++) {
					AbstractValueView[] theValues = theContainer.getAttributeValues(theAtts[i]);
					if (theValues != null) {
						Object[] temp = new Object[2];
						temp[0] = theAtts[i];
						temp[1] = theValues[0];
						ibaContainer.put(theAtts[i].getName(), temp);

					}
					ibaNames.put(theAtts[i].getDisplayName(), theAtts[i].getName());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private AbstractValueView internalCreateValue(AbstractAttributeDefinizerView theDef, String theValue) {
		AbstractValueView theView = null;
		if (theDef instanceof FloatDefView) {
			theView = LoadValue.newFloatValue(theDef, theValue, null);
		} else if (theDef instanceof StringDefView) {
			theView = LoadValue.newStringValue(theDef, theValue);
		} else if (theDef instanceof IntegerDefView) {
			theView = LoadValue.newIntegerValue(theDef, theValue);
		} else if (theDef instanceof RatioDefView) {
			theView = LoadValue.newRatioValue(theDef, theValue, null);
		} else if (theDef instanceof TimestampDefView) {
			theView = LoadValue.newTimestampValue(theDef, theValue);
		} else if (theDef instanceof BooleanDefView) {
			theView = LoadValue.newBooleanValue(theDef, theValue);
		} else if (theDef instanceof URLDefView) {
			theView = LoadValue.newURLValue(theDef, theValue, null);
		} else if (theDef instanceof ReferenceDefView) {
			theView = LoadValue.newReferenceValue(theDef, "ClassificationNode", theValue);
		} else if (theDef instanceof UnitDefView) {
			theView = LoadValue.newUnitValue(theDef, theValue, null);
		}
		return theView;
	}

	public void setIBAValue(String name, String value) throws WTPropertyVetoException {
		AbstractValueView ibaValue = null;
		AttributeDefDefaultView theDef = null;
		Object[] obj = (Object[]) ibaContainer.get(name);
		if (obj != null) {
			ibaValue = (AbstractValueView) obj[1];
			theDef = (AttributeDefDefaultView) obj[0];
		}

		if (ibaValue == null)
			theDef = getAttributeDefinition(name);
		if (theDef == null) {
			return;
		}

		ibaValue = internalCreateValue(theDef, value);
		if (ibaValue == null) {
			return;
		}

		ibaValue.setState(AbstractValueView.CHANGED_STATE);
		Object[] temp = new Object[2];
		temp[0] = theDef;
		temp[1] = ibaValue;

		ibaContainer.put(theDef.getName(), temp);
	}

	@SuppressWarnings("rawtypes")
	public void setIBAValues(String name, Vector values) throws WTPropertyVetoException {
		AttributeDefDefaultView theDef = null;
		Object[] obj = (Object[]) ibaContainer.get(name);
		if (obj != null) {
			theDef = (AttributeDefDefaultView) obj[0];
		}

		if (theDef == null) {
			return;
		}
		Object[] temp = new Object[2];
		temp[0] = theDef;
		for (int i = 0; i < values.size(); i++) {
			String value = (String) values.get(i);
			AbstractValueView ibaValue = internalCreateValue(theDef, value);
			if (ibaValue == null) {
				return;
			}

			ibaValue = (AbstractValueView) obj[1];
			ibaValue.setState(AbstractValueView.CHANGED_STATE);
			temp[i + 1] = ibaValue;
		}

		ibaContainer.put(theDef.getName(), temp);
	}

	public String toString() {
		StringBuffer tempString = new StringBuffer();
		Enumeration<String> res = ibaContainer.keys();
		try {
			while (res.hasMoreElements()) {
				String theKey = (String) res.nextElement();
				AbstractValueView theValue = (AbstractValueView) ((Object[]) ibaContainer.get(theKey))[1];
				tempString.append(theKey + " - " + IBAValueUtility.getLocalizedIBAValueDisplayString(theValue,
						SessionHelper.manager.getLocale()));
				tempString.append('\n');
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (tempString.toString());
	}

	public String translateIBAName(String displayName) {
		return ibaNames.get(displayName).toString();
	}

	@SuppressWarnings("unchecked")
	public <T extends IBAHolder> T updateAttributeContainer(IBAHolder ibaHolder, Class<T> clatt) {
		try {
			return (T) updateIBAPart(ibaHolder);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return (T) ibaHolder;
	}

	public IBAHolder updateIBAPart(IBAHolder ibaHolder) throws Exception {
		ibaHolder = IBAValueHelper.service.refreshAttributeContainer(ibaHolder, null, SessionHelper.manager.getLocale(),
				null);
		DefaultAttributeContainer theContainer = (DefaultAttributeContainer) ibaHolder.getAttributeContainer();

		Enumeration<Object[]> res = ibaContainer.elements();
		while (res.hasMoreElements()) {
			try {
				Object[] temp = (Object[]) res.nextElement();
				AbstractValueView theValue = (AbstractValueView) temp[1];
				AttributeDefDefaultView theDef = (AttributeDefDefaultView) temp[0];
				if (theValue.getState() == AbstractValueView.CHANGED_STATE) {
					theContainer.deleteAttributeValues(theDef);
					theValue.setState(AbstractValueView.NEW_STATE);
					theContainer.addAttributeValue(theValue);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		ibaHolder.setAttributeContainer(theContainer);
		return ibaHolder;
	}

	public IBAHolder updateIBAPart2(IBAHolder ibaHolder) throws Exception {
		ibaHolder = IBAValueHelper.service.refreshAttributeContainer(ibaHolder, null, SessionHelper.manager.getLocale(),
				null);
		DefaultAttributeContainer theContainer = (DefaultAttributeContainer) ibaHolder.getAttributeContainer();

		Enumeration<Object[]> res = ibaContainer.elements();
		while (res.hasMoreElements()) {
			try {
				Object[] temp = (Object[]) res.nextElement();
				AbstractValueView theValue = (AbstractValueView) temp[1];
				AttributeDefDefaultView theDef = (AttributeDefDefaultView) temp[0];
				if (theValue.getState() == AbstractValueView.CHANGED_STATE) {
					theContainer.deleteAttributeValues(theDef);
					theValue.setState(AbstractValueView.NEW_STATE);
					theContainer.addAttributeValue(theValue);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

		ibaHolder.setAttributeContainer(theContainer);
		LoadValue.applySoftAttributes(ibaHolder);
		return ibaHolder;
	}
}
