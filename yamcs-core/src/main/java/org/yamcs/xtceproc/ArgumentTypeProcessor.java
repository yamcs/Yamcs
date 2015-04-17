package org.yamcs.xtceproc;

import java.util.List;

import org.yamcs.ErrorInCommand;
import org.yamcs.protobuf.ValueHelper;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.protobuf.Yamcs.Value.Type;
import org.yamcs.utils.StringConvertors;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.BinaryArgumentType;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.FloatArgumentType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatValidRange;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerRange;
import org.yamcs.xtce.IntegerValidRange;
import org.yamcs.xtce.StringArgumentType;
import org.yamcs.xtce.ValueEnumeration;

public class ArgumentTypeProcessor {
	
	public static Value decalibrate(ArgumentType atype, Value v) {
		if (atype instanceof EnumeratedArgumentType) {
			return decalibrateEnumerated((EnumeratedArgumentType) atype, v);
		} else if (atype instanceof IntegerArgumentType) {
			return decalibrateInteger((IntegerArgumentType) atype, v);
		} else if (atype instanceof FloatArgumentType) {
			return decalibrateFloat((FloatArgumentType) atype, v);
		} else if (atype instanceof StringArgumentType) {
			return decalibrateString((StringArgumentType) atype, v);
		} else if (atype instanceof BinaryArgumentType) {
			return decalibrateBinary((BinaryArgumentType) atype, v);
		} else {
			throw new IllegalArgumentException("decalibration for "+atype+" not implemented");
		}
	}

	private static Value decalibrateEnumerated(EnumeratedArgumentType atype, Value v) {
		if(v.getType()!=Value.Type.STRING) throw new IllegalArgumentException("Enumerated decalibrations only available for strings");
		return Value.newBuilder().setType(Value.Type.SINT64).setSint64Value(atype.decalibrate(v.getStringValue())).build();
	}
	
	
    private static Value decalibrateInteger(IntegerArgumentType ipt, Value v) {
        if (v.getType() == Type.UINT32) {
           return doIntegerDecalibration(ipt, v.getUint32Value()&0xFFFFFFFFL);
        } else if (v.getType() == Type.UINT64) {
            return doIntegerDecalibration(ipt, v.getUint64Value());
        } else if (v.getType() == Type.SINT32) {
            return doIntegerDecalibration(ipt, v.getSint32Value());
        } else if (v.getType() == Type.SINT64) {
            return doIntegerDecalibration(ipt, v.getSint64Value());
        } else if (v.getType() == Type.STRING) {
            return doIntegerDecalibration(ipt, Long.valueOf(v.getStringValue()));
        } else {
            throw new IllegalStateException("Unsupported raw value type '"+v.getType()+"' cannot be converted to integer");
        }
    }
    
    private static Value doIntegerDecalibration(IntegerArgumentType ipt, long v) {
        Calibrator calibrator=null;
        DataEncoding de=ipt.getEncoding();
        if(de instanceof IntegerDataEncoding) {
            calibrator=((IntegerDataEncoding) de).getDefaultCalibrator();
        } else {
            throw new IllegalStateException("Unsupported integer encoding of type: "+de);
        }
        
        Value raw;
        long longDecalValue = (calibrator == null) ? v:calibrator.calibrate(v).longValue(); 
        
        if (ipt.getSizeInBits() <= 32) {
            if (ipt.isSigned()) {
                raw = Value.newBuilder().setType(Value.Type.SINT32).setSint32Value((int) longDecalValue).build();
            } else {
                raw = Value.newBuilder().setType(Value.Type.UINT32).setUint32Value((int) longDecalValue).build();
            }
        } else {
            if (ipt.isSigned()) {
            	raw = Value.newBuilder().setType(Value.Type.UINT32).setSint64Value(longDecalValue).build();            	
            } else {
            	raw = Value.newBuilder().setType(Value.Type.UINT32).setUint64Value(longDecalValue).build();
            }
        }
        return raw;
    }

	
	private static Value decalibrateFloat(FloatArgumentType fat, Value v) {
        if(v.getType() == Type.FLOAT) {
            return doFloatDecalibration(fat, v.getFloatValue());
        } else if(v.getType() == Type.DOUBLE) {
        	return doFloatDecalibration(fat, v.getDoubleValue());
        } else if(v.getType() == Type.STRING) {
        	return doFloatDecalibration(fat, Double.valueOf(v.getStringValue()));
        } else if(v.getType() == Type.UINT32) {
        	return doFloatDecalibration(fat, v.getUint32Value());
        } else if(v.getType() == Type.UINT64) {
        	return doFloatDecalibration(fat, v.getUint64Value());
        } else if(v.getType() == Type.SINT32) {
        	return doFloatDecalibration(fat, v.getSint32Value());
        } else if(v.getType() == Type.SINT64) {
        	return  doFloatDecalibration(fat, v.getSint64Value());
        } else {
            throw new IllegalArgumentException("Unsupported value type '"+v.getType()+"' cannot be converted to float");
        }
    }
    
    private static Value doFloatDecalibration(FloatArgumentType fat, double doubleValue) {
        Calibrator calibrator=null;
        DataEncoding de=fat.getEncoding();
        if(de instanceof FloatDataEncoding) {
            calibrator=((FloatDataEncoding) de).getDefaultCalibrator();
        } else if(de instanceof IntegerDataEncoding) {
            calibrator=((IntegerDataEncoding) de).getDefaultCalibrator();
        } else {
            throw new IllegalStateException("Unsupported float encoding of type: "+de);
        }
        
        double doubleCalValue = (calibrator == null) ? doubleValue:calibrator.calibrate(doubleValue);
        Value raw;
        if(fat.getSizeInBits() == 32) {
            raw = Value.newBuilder().setType(Value.Type.FLOAT).setFloatValue((float)doubleCalValue).build();
        } else {
        	raw = Value.newBuilder().setType(Value.Type.DOUBLE).setDoubleValue(doubleCalValue).build();
        }
        return raw;
    }    

    private static Value decalibrateString(StringArgumentType sat, Value v) {
    	Value raw;
    	if(v.getType() == Type.STRING) {
    		raw = v;
        } else {
            throw new IllegalStateException("Unsupported value type '"+v.getType()+"' cannot be converted to string");
        }
    	return raw;
    }
    
    
    private static Value decalibrateBinary(BinaryArgumentType bat, Value v) {
    	Value raw;
    	if(v.getType() == Type.BINARY) {
    		raw = v;
        } else {
            throw new IllegalStateException("Unsupported value type '"+v.getType()+"' cannot be converted to binary");
        }
    	return raw;
    }

	public static Value parseAndCheckRange(ArgumentType type, String argumentValue) throws ErrorInCommand {
		Value v;
		if(type instanceof IntegerArgumentType) {
			long l = Long.decode(argumentValue);
			IntegerValidRange vr = ((IntegerArgumentType)type).getValidRange();
			if(vr!=null) {
				if(!ValidRangeChecker.checkIntegerRange(vr, l)) {
					throw new ErrorInCommand("Value "+l+" is not in the range required for the type "+type);
				}
			}
			v = ValueHelper.newValue(l);
		} else if(type instanceof FloatArgumentType) {
			double d = Double.parseDouble(argumentValue);
			FloatValidRange vr = ((FloatArgumentType)type).getValidRange();
			if(vr!=null) {
				if(!ValidRangeChecker.checkFloatRange(vr, d)) {
					throw new ErrorInCommand("Value "+d+" is not in the range required for the type "+type);
				}
			}
			v = ValueHelper.newValue(d);
		} else if(type instanceof StringArgumentType) {
			v = ValueHelper.newValue(argumentValue);
			IntegerRange r = ((StringArgumentType)type).getSizeRangeInCharacters();
			
			if(r!=null) {
				int length = argumentValue.length();
				if (length<r.getMinInclusive()) {
					throw new ErrorInCommand("Value "+argumentValue+" supplied for parameter fo type "+type+" does not satisfy minimum length of "+r.getMinInclusive());
				}
				if(length>r.getMaxInclusive()) {
					throw new ErrorInCommand("Value "+argumentValue+" supplied for parameter fo type "+type+" does not satisfy maximum length of "+r.getMaxInclusive());
				}
			}
			
		} else if (type instanceof BinaryArgumentType) {
			byte[] b = StringConvertors.hexStringToArray(argumentValue);
			v = ValueHelper.newValue(b);
		} else if (type instanceof EnumeratedArgumentType) {
			EnumeratedArgumentType enumType = (EnumeratedArgumentType)type;
			List<ValueEnumeration> vlist = enumType.getValueEnumerationList();
			boolean found =false;
			for(ValueEnumeration ve:vlist) {
				if(ve.getLabel().equals(argumentValue)) {
					found = true;
				}
			}
			if(!found) {
				throw new ErrorInCommand("Value '"+argumentValue+"' supplied for enumeration argument cannot be found in enumeration list "+vlist);
			}
			v = ValueHelper.newValue(argumentValue);
		} else {
			throw new IllegalArgumentException("Cannot parse values of type "+type);
		}
		return v;
	}

}
