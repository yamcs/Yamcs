package org.yamcs.xtce.xml;

public final class Constants {
    public static final String ATTR_PARAMETER_REF = "parameterRef";
    public static final String ATTR_SHORT_DESCRIPTION = "shortDescription";
    public static final String ATTR_NAME = "name";
    public static final String ATTR_INITIAL_VALUE = "initialValue";
    public static final String ATTR_SIZE_IN_BITS = "sizeInBits";
    public static final String ATTR_ENCODING = "encoding";

    public static final String ELEM_RELATIVE_TIME_PARAMETER_TYPE = "RelativeTimeParameterType";
    public static final String ELEM_ARRAY_PARAMETER_TYPE = "ArrayParameterType";
    public static final String ELEM_AGGREGATE_PARAMETER_TYPE = "AggregateParameterType";
    public static final String ELEM_AGGREGATE_ARGUMENT_TYPE = "AggregateArgumentType";

    public static final String ELEM_AUTHOR_SET = "AuthorSet";
    public static final String ELEM_NOTE_SET = "NoteSet";
    public static final String ELEM_HISTORY_SET = "HistorySet";

    public static final String ELEM_TELEMTRY_META_DATA = "TelemetryMetaData";
    public static final String ELEM_PARAMETER_TYPE_SET = "ParameterTypeSet";
    public static final String ELEM_BOOLEAN_PARAMETER_TYPE = "BooleanParameterType";
    public static final String ELEM_ENUMERATED_PARAMETER_TYPE = "EnumeratedParameterType";
    public static final String ELEM_ENUMERATION_LIST = "EnumerationList";
    public static final String ELEM_ENUMERATION = "Enumeration";
    public static final String ELEM_RANGE_ENUMERATION = "RangeEnumeration";
    public static final String ELEM_STRING_PARAMETER_TYPE = "StringParameterType";
    public static final String ELEM_BINARY_PARAMETER_TYPE = "BinaryParameterType";
    public static final String ELEM_INTEGER_PARAMETER_TYPE = "IntegerParameterType";
    public static final String ELEM_FLOAT_PARAMETER_TYPE = "FloatParameterType";
    public static final String ELEM_SPACE_SYSTEM = "SpaceSystem";
    public static final String ELEM_ALIAS_SET = "AliasSet";
    public static final String ELEM_ALIAS = "Alias";
    public static final String ELEM_LONG_DESCRIPTION = "LongDescription";
    public static final String ELEM_HEADER = "Header";
    public static final String ELEM_ABSOLUTE_TIME_PARAMETER_TYPE = "AbsoluteTimeParameterType";
    public static final String ELEM_ABSOLUTE_TIME_ARGUMENT_TYPE = "AbsoluteTimeArgumentType";
    public static final String ELEM_PARAMETER_SET = "ParameterSet";
    public static final String ELEM_PARAMETER = "Parameter";
    public static final String ELEM_PARAMETER_REF = "ParameterRef";
    public static final String ELEM_PARAMETER_PROPERTIES = "ParameterProperties";
    public static final String ELEM_VALIDITY_CONDITION = "ValidityCondition";
    public static final String ELEM_COMPARISON_LIST = "ComparisonList";
    public static final String ELEM_COMPARISON = "Comparison";
    public static final String ELEM_BOOLEAN_EXPRESSION = "BooleanExpression";
    public static final String ELEM_CUSTOM_ALGORITHM = "CustomAlgorithm";
    public static final String ELEM_MATH_ALGORITHM = "MathAlgorithm";
    public static final String ELEM_RESTRICTION_CRITERIA = "RestrictionCriteria";
    public static final String ELEM_SYSTEM_NAME = "SystemName";
    public static final String ELEM_PHYSICAL_ADDRESS_SET = "PhysicalAddressSet";
    public static final String ELEM_TIME_ASSOCIATION = "TimeAssociation";
    public static final String ELEM_CONTAINER_SET = "ContainerSet";
    public static final String ELEM_COMMAND_CONTAINER_SET = "CommandContainerSet";
    public static final String ELEM_BASE_CONTAINER = "BaseContainer";
    public static final String ELEM_MESSAGE_SET = "MessageSet";
    public static final String ELEM_STREAM_SET = "StreamSet";
    public static final String ELEM_ALGORITHM_SET = "AlgorithmSet";

    public static final String ELEM_COMMAND_MEATA_DATA = "CommandMetaData";
    public static final String ELEM_SEQUENCE_CONTAINER = "SequenceContainer";
    public static final String ELEM_ENTRY_LIST = "EntryList";
    public static final String ELEM_PARAMETER_REF_ENTRY = "ParameterRefEntry";

    public static final String ELEM_LOCATION_IN_CONTAINER_IN_BITS = "LocationInContainerInBits";
    public static final String ELEM_REPEAT_ENTRY = "RepeatEntry";
    public static final String ELEM_INCLUDE_CONDITION = "IncludeCondition";

    public static final String ELEM_PARAMETER_SEGMENT_REF_ENTRY = "ParameterSegmentRefEntry";
    public static final String ELEM_CONTAINER_REF_ENTRY = "ContainerRefEntry";
    public static final String ELEM_CONTAINER_SEGMENT_REF_ENTRY = "ContainerSegmentRefEntry";
    public static final String ELEM_STREAM_SEGMENT_ENTRY = "StreamSegmentEntry";
    public static final String ELEM_INDIRECT_PARAMETER_REF_ENTRY = "IndirectParameterRefEntry";
    public static final String ELEM_ARRAY_PARAMETER_REF_ENTRY = "ArrayParameterRefEntry";

    public static final String ELEM_UNIT_SET = "UnitSet";
    public static final String ELEM_UNIT = "Unit";
    public static final String ELEM_FLOAT_DATA_ENCODING = "FloatDataEncoding";
    public static final String ELEM_BINARY_DATA_ENCODING = "BinaryDataEncoding";
    public static final String ELEM_SIZE_IN_BITS = "SizeInBits";
    public static final String ELEM_VARIABLE = "Variable";
    public static final String ELEM_FIXED_VALUE = "FixedValue";
    public static final String ELEM_DYNAMIC_VALUE = "DynamicValue";
    public static final String ELEM_LINEAR_ADJUSTMENT = "LinearAdjustment";
    public static final String ELEM_DISCRETE_LOOKUP_LIST = "DiscreteLookupList";
    public static final String ELEM_INTEGER_DATA_ENCODING = "IntegerDataEncoding";
    public static final String ELEM_STRING_DATA_ENCODING = "StringDataEncoding";
    public static final String ELEM_CONTEXT_ALARM_LIST = "ContextAlarmList";
    public static final String ELEM_CONTEXT_ALARM = "ContextAlarm";
    public static final String ELEM_CONTEXT_MATCH = "ContextMatch";
    public static final String ELEM_DEFAULT_CALIBRATOR = "DefaultCalibrator";
    public static final String ELEM_CALIBRATOR = "Calibrator";
    public static final String ELEM_CONTEXT_CALIBRATOR = "ContextCalibrator";
    public static final String ELEM_CONTEXT_CALIBRATOR_LIST = "ContextCalibratorList";
    public static final String ELEM_SPLINE_CALIBRATOR = "SplineCalibrator";
    public static final String ELEM_POLYNOMIAL_CALIBRATOR = "PolynomialCalibrator";
    public static final String ELEM_MATH_OPERATION_CALIBRATOR = "MathOperationCalibrator";
    public static final String ELEM_TERM = "Term";
    public static final String ELEM_SPLINE_POINT = "SplinePoint";
    public static final String ELEM_COUNT = "Count";
    public static final String ELEM_PARAMETER_INSTANCE_REF = "ParameterInstanceRef";
    public static final String ELEM_ARGUMENT_INSTANCE_REF = "ArgumentInstanceRef";
    public static final String ELEM_STATIC_ALARM_RANGES = "StaticAlarmRanges";
    public static final String ELEM_DEFAULT_ALARM = "DefaultAlarm";
    public static final String ELEM_FIXED = "Fixed";
    public static final String ELEM_TERMINATION_CHAR = "TerminationChar";
    public static final String ELEM_LEADING_SIZE = "LeadingSize";
    public static final String ELEM_DEFAULT_RATE_IN_STREAM = "DefaultRateInStream";
    public static final String ELEM_REFERENCE_TIME = "ReferenceTime";
    public static final String ELEM_OFFSET_FROM = "OffsetFrom";
    public static final String ELEM_EPOCH = "Epoch";
    public static final String ELEM_ENCODING = "Encoding";
    public static final String ELEM_ARGUMENT_TYPE_SET = "ArgumentTypeSet";
    public static final String ELEM_META_COMMAND_SET = "MetaCommandSet";
    public static final String ELEM_META_COMMAND = "MetaCommand";
    public static final String ELEM_COMMAND_CONTAINER = "CommandContainer";
    public static final String ELEM_STRING_ARGUMENT_TYPE = "StringArgumentType";
    public static final String ELEM_BINARY_ARGUMENT_TYPE = "BinaryArgumentType";
    public static final String ELEM_INTEGER_ARGUMENT_TYPE = "IntegerArgumentType";
    public static final String ELEM_FLOAT_ARGUMENT_TYPE = "FloatArgumentType";
    public static final String ELEM_BOOLEAN_ARGUMENT_TYPE = "BooleanArgumentType";
    public static final String ELEM_ENUMERATED_ARGUMENT_TYPE = "EnumeratedArgumentType";
    public static final String ELEM_ARRAY_ARGUMENT_TYPE = "ArrayArgumentType";

    public static final String ELEM_BASE_META_COMMAND = "BaseMetaCommand";
    public static final String ELEM_ARGUMENT_LIST = "ArgumentList";
    public static final String ELEM_ARGUMENT_ASSIGNMENT_LIST = "ArgumentAssignmentList";
    public static final String ELEM_ARGUMENT = "Argument";
    public static final String ELEM_ARGUMENT_REF_ENTRY = "ArgumentRefEntry";
    public static final String ELEM_ARRAY_ARGUMENT_REF_ENTRY = "ArrayArgumentRefEntry";
    public static final String ELEM_FIXED_VALUE_ENTRY = "FixedValueEntry";
    public static final String ELEM_VALUE_OPERAND = "ValueOperand";
    public static final String ELEM_MATH_OPERATION = "MathOperation";
    public static final String ELEM_TRIGGER_SET = "TriggerSet";
    public static final String ELEM_OUTPUT_SET = "OutputSet";
    public static final String ELEM_INPUT_SET = "InputSet";
    public static final String ELEM_INPUT_PARAMETER_INSTANCE_REF = "InputParameterInstanceRef";
    public static final String ELEM_INPUT_ARGUMENT_INSTANCE_REF = "InputArgumentInstanceRef";
    public static final String ELEM_CONSTANT = "Constant";
    public static final String ELEM_OUTPUT_PARAMETER_REF = "OutputParameterRef";
    public static final String ELEM_ALGORITHM_TEXT = "AlgorithmText";
    public static final String ELEM_ARGUMENT_ASSIGNMENT = "ArgumentAssignment";
    public static final String ELEM_MEMBER_LIST = "MemberList";
    public static final String ELEM_MEMBER = "Member";
    public static final String ELEM_DIMENSION_LIST = "DimensionList";
    public static final String ELEM_SIZE = "Size";
    public static final String ELEM_DIMENSION = "Dimension";
    public static final String ELEM_STARTING_INDEX = "StartingIndex";
    public static final String ELEM_ENDING_INDEX = "EndingIndex";
    public static final String ELEM_ANCILLARY_DATA_SET = "AncillaryDataSet";
    public static final String ELEM_ANCILLARY_DATA = "AncillaryData";
    public static final String ELEM_VALID_RANGE = "ValidRange";
    public static final String ELEM_VALID_RANGE_SET = "ValidRangeSet";
    public static final String ELEM_TO_STRING = "ToString";
    public static final String ELEM_NUMBER_FORMAT = "NumberFormat";
    public static final String ELEM_BINARY_ENCODING = "BinaryEncoding";
    public static final String ELEM_DEFAULT_SIGNIFICANCE = "DefaultSignificance";
    public static final String ELEM_VERIFIER_SET = "VerifierSet";
    public static final String ELEM_CONTAINER_REF = "ContainerRef";
    public static final String ELEM_CHECK_WINDOW = "CheckWindow";
    public static final String ELEM_CONDITION = "Condition";
    public static final String ELEM_AND_CONDITIONS = "ANDedConditions";
    public static final String ELEM_OR_CONDITIONS = "ORedConditions";
    public static final String ELEM_COMPARISON_OPERATOR = "ComparisonOperator";
    public static final String ELEM_VALUE = "Value";
    public static final String ELEM_TRANSMISSION_CONSTRAINT = "TransmissionConstraint";
    public static final String ELEM_TRANSMISSION_CONSTRAINT_LIST = "TransmissionConstraintList";
    public static final String ELEM_PARAMETER_VALUE_CHANGE = "ParameterValueChange";
    public static final String ELEM_CHANGE = "Change";
    public static final String ELEM_RETURN_PARAM_REF = "ReturnParmRef";
}
