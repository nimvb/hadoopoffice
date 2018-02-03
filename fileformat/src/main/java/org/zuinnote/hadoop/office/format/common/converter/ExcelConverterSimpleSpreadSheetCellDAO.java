/**
* Copyright 2018 ZuInnoTe (Jörn Franke) <zuinnote@gmail.com>
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
**/

package org.zuinnote.hadoop.office.format.common.converter;


import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.ss.util.CellAddress;
import org.zuinnote.hadoop.office.format.common.converter.datatypes.GenericBigDecimalDataType;
import org.zuinnote.hadoop.office.format.common.converter.datatypes.GenericBooleanDataType;
import org.zuinnote.hadoop.office.format.common.converter.datatypes.GenericByteDataType;
import org.zuinnote.hadoop.office.format.common.converter.datatypes.GenericDataType;
import org.zuinnote.hadoop.office.format.common.converter.datatypes.GenericDateDataType;
import org.zuinnote.hadoop.office.format.common.converter.datatypes.GenericIntegerDataType;
import org.zuinnote.hadoop.office.format.common.converter.datatypes.GenericLongDataType;
import org.zuinnote.hadoop.office.format.common.converter.datatypes.GenericNumericDataType;
import org.zuinnote.hadoop.office.format.common.converter.datatypes.GenericShortDataType;
import org.zuinnote.hadoop.office.format.common.converter.datatypes.GenericStringDataType;
import org.zuinnote.hadoop.office.format.common.dao.SpreadSheetCellDAO;

/**
 * This class allows to infer the Java datatypes underlying a SpreadSheet and the corresponding data as Java objects
 *
 */
public class ExcelConverterSimpleSpreadSheetCellDAO {
	private static final Log LOG = LogFactory.getLog(ExcelConverterSimpleSpreadSheetCellDAO.class.getName());
	
	
	private List<GenericDataType> schemaRow;
	private SimpleDateFormat dateFormat;
	private DecimalFormat decimalFormat;


/**
 * 
 * @param dateFormat
 * @param decimalLocale
 * @param dformat
 */
	public ExcelConverterSimpleSpreadSheetCellDAO(SimpleDateFormat dateFormat, DecimalFormat decimalFormat) {
		this.schemaRow=new ArrayList<>();
		this.dateFormat=dateFormat;
		this.decimalFormat=decimalFormat;
        this.decimalFormat.setParseBigDecimal(true);
	}
	
	/***
	 * This provides another sample to infer schema in form of simple datatypes (e.g. boolean, byte etc.). You might add as many sample as necessary to get a precise schema. 
	 * 
	 * @param dataRow
	 */
	public void updateSpreadSheetCellRowToInferSchemaInformation(SpreadSheetCellDAO[] dataRow) {
		// check size of cell based on address
		// if necessary add more to schemaRow
	
		for (SpreadSheetCellDAO currentSpreadSheetCellDAO: dataRow) {
			boolean dataTypeFound = false;
			if (currentSpreadSheetCellDAO!=null) {
				// add potential column to list
				int j = new CellAddress(currentSpreadSheetCellDAO.getAddress()).getColumn();
				if (j>=this.schemaRow.size()) {
					// fill up
					for (int x=this.schemaRow.size();x<=j;x++) {
						this.schemaRow.add(null);
					}
				}
				if ((currentSpreadSheetCellDAO.getFormattedValue()!=null) && (!"".equals(currentSpreadSheetCellDAO.getFormattedValue()))) { // skip null value
					String currentCellValue = currentSpreadSheetCellDAO.getFormattedValue();
					// check if boolean
		              if (("TRUE".equals(currentCellValue)) || ("FALSE".equals(currentCellValue))) {
		                  dataTypeFound = true;
		                  if (this.schemaRow.get(j) != null) { // check if previous assumption was boolean
		                
		                    if (!(this.schemaRow.get(j) instanceof GenericBooleanDataType)) {
		                      // if not then the type needs to be set to string
		                      this.schemaRow.set(j, new GenericStringDataType());
		                    }
		                    // if yes then nothing todo (already boolean)
		                  } else { // we face this the first time
		                	  	this.schemaRow.set(j, new GenericBooleanDataType());
		                  }
		                }
		         
		              if (!dataTypeFound) {

		                Date theDate = this.dateFormat.parse(currentCellValue, new ParsePosition(0));
		                if (theDate != null) { // we have indeed a date

		                  dataTypeFound = true;
		                  if (this.schemaRow.get(j) != null) { // check if previous assumption was date
		                    
		                    if (!(this.schemaRow.get(j) instanceof GenericDateDataType)) {
		                      // if not then the type needs to be set to string
		                     this.schemaRow.set(j, new GenericStringDataType());
		                    }
		                  } else { // we face this the first time
		                	   this.schemaRow.set(j, new GenericDateDataType());
		                  }
		                }
		              }
		              // check if BigDecimal
		           
		              BigDecimal bd = (BigDecimal) this.decimalFormat.parse(currentCellValue, new ParsePosition(0));
		              if ((!dataTypeFound) && (bd != null)) {
		                BigDecimal bdv = bd.stripTrailingZeros();

		                dataTypeFound = true;

		                if (this.schemaRow.get(j) != null) { // check if previous assumption was a number
		                  
		                  // check if we need to upgrade to decimal
		                  if ((bdv.scale() > 0) && (this.schemaRow.get(j) instanceof GenericNumericDataType)) {
		                    // upgrade to decimal, if necessary
		                    if (!(this.schemaRow.get(j) instanceof GenericBigDecimalDataType)) {
		                    	  this.schemaRow.set(j, new GenericBigDecimalDataType(bdv.precision(),bdv.scale()));
		                    } else {
		                      if ((bdv.scale() > ((GenericBigDecimalDataType)this.schemaRow.get(j)).getScale()) && (bdv.precision() > ((GenericBigDecimalDataType)this.schemaRow.get(j)).getPrecision())) {
		                       this.schemaRow.set(j, new GenericBigDecimalDataType(bdv.precision(),bdv.scale()));
		                      } else if (bdv.scale() > ((GenericBigDecimalDataType)this.schemaRow.get(j)).getScale()) {
		                        // upgrade scale
		                    	  	GenericBigDecimalDataType gbd = ((GenericBigDecimalDataType)this.schemaRow.get(j));
		                    	  	gbd.setScale(bdv.scale());
		                    	  	this.schemaRow.set(j, gbd);
		                       } else if (bdv.precision() > ((GenericBigDecimalDataType)this.schemaRow.get(j)).getPrecision()) {
		                        // upgrade precision
		                        // new precision is needed to extend to max scale
		                    	  	GenericBigDecimalDataType gbd = ((GenericBigDecimalDataType)this.schemaRow.get(j));
		                    	  	int newpre = bdv.precision() + (gbd.getScale() - bdv.scale());
		                    	  	gbd.setPrecision(newpre);
		                    	  	this.schemaRow.set(j, gbd);
		                      }
		                    }
		                  } else { // check if we need to upgrade one of the integer types
		                    // if current is byte
		                    boolean isByte = false;
		                    boolean isShort = false;
		                    boolean isInt = false;
		                    	boolean isLong = true;
		                    try {
		                      bdv.longValueExact();
		                      isLong = true;
		                      bdv.intValueExact();
		                      isInt = true;
		                      bdv.shortValueExact();
		                      isShort = true;
		                      bdv.byteValueExact();
		                      isByte = true;
		                    } catch (Exception e) {
		                    		LOG.debug("Possible data types: Long: " + isLong + " Int: " + isInt + " Short: " + isShort + " Byte: " + isByte);
		                    }
		                    // if it was Numeric before we can ignore testing the byte case, here just for completeness
		                    if ((isByte) && ((this.schemaRow.get(j) instanceof GenericByteDataType) || (this.schemaRow.get(j) instanceof GenericShortDataType) || (this.schemaRow.get(j) instanceof GenericIntegerDataType) || (this.schemaRow.get(j) instanceof GenericLongDataType))) {
		                      // if it was Byte before we can ignore testing the byte case, here just for completeness
		                    } else if ((isShort) && ((this.schemaRow.get(j) instanceof GenericByteDataType))) {
		                      // upgrade to short
		                    	 this.schemaRow.set(j, new GenericShortDataType());
		                    } else if ((isInt) && ((this.schemaRow.get(j) instanceof GenericShortDataType) || (this.schemaRow.get(j) instanceof GenericByteDataType))) {
		                      // upgrade to integer
		                    	 this.schemaRow.set(j, new GenericIntegerDataType());
		                    } else if ((!isByte) && (!isShort) && (!isInt) && !((this.schemaRow.get(j) instanceof GenericLongDataType))) {
		                      // upgrade to long
		                    	 this.schemaRow.set(j, new GenericLongDataType());
		                    }

		                  }

		                } else {
		                  // we face it for the first time
		                  // determine value type
		                  if (bdv.scale() > 0) {
		                	  	this.schemaRow.set(j, new GenericBigDecimalDataType(bdv.precision(),bdv.scale()));
		                  } else {
		                    boolean isByte = false;
		                    boolean isShort = false;
		                    boolean isInt = false;
		                    	boolean isLong = true;
		                    try {
		                      bdv.longValueExact();
		                      isLong = true;
		                      bdv.intValueExact();
		                      isInt = true;
		                      bdv.shortValueExact();
		                      isShort = true;
		                      bdv.byteValueExact();
		                      isByte = true;
		                    } catch (Exception e){
		                    		LOG.debug("Possible data types: Long: " + isLong + " Int: " + isInt + " Short: " + isShort + " Byte: " + isByte);
		                    }
		                    if (isByte) {
		                    	 this.schemaRow.set(j, new GenericByteDataType());
		                    } else if (isShort) {
		                    	this.schemaRow.set(j, new GenericShortDataType());
		                    } else if (isInt) {
		                    	this.schemaRow.set(j, new GenericIntegerDataType());
		                    } else if (isLong) {
		                    	this.schemaRow.set(j, new GenericLongDataType());
		                    }
		                  }
		                }
		              }
		              if (!dataTypeFound) {
		                // otherwise string
		            	  if (!(this.schemaRow.get(j) instanceof GenericStringDataType)) {
		            		  this.schemaRow.set(j,new GenericStringDataType());
		            	  }
		               
		              }


		            
			} else {
				// ignore null values
			}
		}
	}
	}
	
	/**
	 * Returns a list of objects corresponding to the schema. 
	 * 
	 * @return
	 */
	public GenericDataType[] getSchemaRow() {
		return this.getSchemaRow();
	}
	
	/**
	 * Translate a data row according to the currently defined schema. 
	 * 
	 * @param dataRow cells containing data
	 * @return an array of objects of primitive datatypes (boolean, int, byte, etc.) containing the data of datarow, null if dataRow does not fit into schema. Note: single elements can be null depending on the original Excel
	 * @throws ParseException 
	 */
	public Object[] getDataAccordingToSchema(SpreadSheetCellDAO[] dataRow) throws ParseException {
		if (dataRow.length!=this.schemaRow.size()) {
			LOG.error("Data row does not fit into schema. Cannot convert spreadsheet cell to simple datatypes");
			return null;
		}
		Object[] result = new Object[dataRow.length];
		for (int i=0;i<dataRow.length;i++) {
			SpreadSheetCellDAO currentCell = dataRow[i];
			if (currentCell!=null) {
				GenericDataType applyDataType = this.schemaRow.get(i);
				if (applyDataType==null) {
					result[i]=currentCell.getFormattedValue();
				} else
				if (applyDataType instanceof GenericStringDataType) {
					result[i]=currentCell.getFormattedValue();
				} else 
				if (applyDataType instanceof GenericBooleanDataType) {
					result[i]=Boolean.valueOf(currentCell.getFormattedValue());
				} else 
				if (applyDataType instanceof GenericDateDataType) {
					Date theDate = this.dateFormat.parse(currentCell.getFormattedValue(), new ParsePosition(0));
			        if (theDate != null) {
			        			result[i]=theDate;
			 
			        } else {
			            result[i]=null;
			        } 
				}	else if (applyDataType instanceof GenericNumericDataType) {
			        		BigDecimal bd = (BigDecimal) this.decimalFormat.parse(currentCell.getFormattedValue());
			        		if (bd!=null) {
			        				BigDecimal bdv = bd.stripTrailingZeros();
			        				if (applyDataType instanceof GenericByteDataType) {
			        					result[i] = bdv.byteValueExact();
			        				} else if (applyDataType instanceof GenericShortDataType) {
			        					result[i] = bdv.shortValueExact();
			        				} else if (applyDataType instanceof GenericIntegerDataType) {
			        					result[i] = bdv.intValueExact();
			        				} else if (applyDataType instanceof GenericLongDataType) {
			        					result[i] = bdv.longValueExact();
			        				} else if (applyDataType instanceof GenericBigDecimalDataType) {
			        					result[i] = bdv;
			        				} else {
			        					result[i] = null;
			        				}
			        }
			        } else {
			        		result[i] = null;
			        		LOG.warn("Could not convert object in spreadsheet cellrow. Did you add a new datatype?");
			        }
			}
		}
		return result;
	}
	
}