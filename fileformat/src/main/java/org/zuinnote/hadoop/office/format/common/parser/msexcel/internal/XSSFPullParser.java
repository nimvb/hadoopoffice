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
package org.zuinnote.hadoop.office.format.common.parser.msexcel.internal;

import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.util.CellAddress;
import org.apache.poi.util.StaxHelper;
import org.apache.poi.xssf.model.SharedStringsTable;
import org.apache.poi.xssf.model.StylesTable;
import org.zuinnote.hadoop.office.format.common.dao.SpreadSheetCellDAO;
import org.zuinnote.hadoop.office.format.common.parser.msexcel.MSExcelParser;

/**
 * Parses .xlsx files in pull mode instead of push
 *
 */
public class XSSFPullParser {
	public static final String CELLTYPE_STRING = "s";

	public static final String CELLTYPE_NUMBER = "s";
	public static final String CELL_NOT_PROCESSABLE ="not processable";
	private static final Log LOG = LogFactory.getLog(XSSFPullParser.class.getName());
	private boolean nextBeingCalled;
	private boolean finalized;
	private int nextRow;
	private int currentRow;
	private XMLEventReader xer;
	private SharedStringsTable sst;
	private StylesTable styles;
	private String sheetName;
	boolean isDate1904;
	
	/**
	 * 
	 * @param sheetName name of sheet
	 * @param sheetInputStream sheet in xlsx format input stream
	 * @param sst Shared strings table of Excel file
	 * @param styles StylesTable of the document
	 * @param isDate1904 date format 1904 (true) or 1900 (false)
	 * @throws XMLStreamException 
	 */
	public XSSFPullParser(String sheetName, InputStream sheetInputStream, SharedStringsTable sst, StylesTable styles, boolean isDate1904) throws XMLStreamException {
		this.sheetName=sheetName;
		this.xer = StaxHelper.newXMLInputFactory().createXMLEventReader(sheetInputStream);
		this.nextBeingCalled=false;
		this.finalized=false;
		this.nextRow=1;
		this.currentRow=1;
		this.sst=sst;
		this.styles=styles;
		this.isDate1904=isDate1904;
	}
	
	public boolean hasNext() throws XMLStreamException {
		this.nextBeingCalled=true;
		if (this.finalized) { // we finished already - no more to read
			return false;
		}
		// search for the next row
		while (this.xer.hasNext()) {
			XMLEvent xe = xer.nextEvent();
			if (xe.isStartElement() && xe.asStartElement().getName().getLocalPart().equalsIgnoreCase("row")) { // we found a row
				// get row number.
				Attribute at = xe.asStartElement().getAttributeByName(new QName("r"));
				String atValue = at.getValue();
				this.nextRow = Integer.valueOf(atValue);
				return true;
			}
		}
		this.finalized=true;
		return false;
	}
	
	public Object[] getNext() throws XMLStreamException {
		Object[] result=null;
		if (!this.nextBeingCalled) { // skip to the next tag
			
			if (this.hasNext()==false) {
				
				return null;
			}
		}
		if (this.finalized) { // no more to read
			return null;
		}
		// check
		ArrayList<SpreadSheetCellDAO> cells = new ArrayList<>();
		if (this.currentRow==this.nextRow) { // only if we have a row to report
			// read through row, cf. http://download.microsoft.com/download/3/E/3/3E3435BD-AA68-4B32-B84D-B633F0D0F90D/SpreadsheetMLBasics.ppt
			int currentCellCount=0;
			while (this.xer.hasNext()) {
				XMLEvent xe = xer.nextEvent();
				// read+
				if (xe.isEndElement()) {
					if (xe.asEndElement().getName().getLocalPart().equalsIgnoreCase("row")) {
						break; // end of row
					}
				} else if (xe.isStartElement()) {
				if (xe.asStartElement().getName().getLocalPart().equalsIgnoreCase("c")) {
					
					Attribute cellAddressAT = xe.asStartElement().getAttributeByName(new QName("r"));
					// check if cell is a subsequent cell and add null, if needed
					CellAddress currentCellAddress = new CellAddress(cellAddressAT.getValue());
					for (int i=currentCellCount;i<currentCellAddress.getColumn();i++) {
						cells.add(null);
						currentCellCount++;
					}
					currentCellCount++; // current cell
					Attribute cellTypeTAT = xe.asStartElement().getAttributeByName(new QName("t"));
					LOG.debug(cellTypeTAT.getValue());
					Attribute cellTypeSAT = xe.asStartElement().getAttributeByName(new QName("s"));
					XMLEvent xeSub = xer.nextEvent();
					if (xeSub.isStartElement()) {
						if (xeSub.asStartElement().getName().getLocalPart().equalsIgnoreCase("v")) {
							
							// if a cell data type is set (e.g. b boolean, d date in ISO8601 format, e error, inlineStr, n number, s shared string, str formula string 
								// we return as string
							if (cellTypeTAT!=null) {
								XMLEvent xeSubCharacters = xer.nextEvent();
								if (!xeSubCharacters.isCharacters()) {
									LOG.error("Error parsing excel file. Value attribute (v) of cell does not contains characters");
								} else {
									String formattedValue = xeSubCharacters.asCharacters().getData();
			
									if (XSSFPullParser.CELLTYPE_STRING.equals(cellTypeTAT.getValue())) { // need to read from Shared String Table
										int strIdx = Integer.valueOf(formattedValue);
										formattedValue=this.sst.getItemAt(strIdx).getString();
									} else if (XSSFPullParser.CELLTYPE_NUMBER.equals(cellTypeTAT.getValue())){
										// need to read number
										// check if t=null => in theory: we need to read styles. 
										/*int strStyleIdx = Integer.valueOf(cellTypeSAT.getValue());
										String dataFormat= this.styles.getStyleAt(strStyleIdx).getDataFormatString();
										LOG.debug(dataFormat);;*
										/*XMLEvent xeSubCharacters = xer.nextEvent();
										if (!xeSubCharacters.isCharacters()) {
											LOG.error("Error parsing excel file. Value attribute (v) of cell does not contains characters");
										} else {
											String formattedValue = xeSubCharacters.asCharacters().getData();
											BigDecimal bd = (BigDecimal) DecimalFormat.getInstance(Locale.US).parse(formattedValue, new ParsePosition(0));
											if (bd==null) {
												LOG.error("Excepted number for formatted data type");
											}
											Date excelDate = DateUtil.getJavaDate(bd.doubleValue(),this.isDate1904);
											
										}*/
									}
									
									String comment = "";
									String formula = "";
									String address = cellAddressAT.getValue();
									String sheetName = this.sheetName;
									cells.add(new SpreadSheetCellDAO(formattedValue, comment, formula, address, sheetName));
								}
							} else { //celltype t = null (e.g. in case of dates)
								LOG.error("Cannot read celltype");
							}
						} else if (xeSub.asStartElement().getName().getLocalPart().equalsIgnoreCase("f")) {
							// if formula check if it is just a number => put it also as value
								// a number may contain only numbers , or . (the exact interpretation as decimal is done at another part of the hadoopoffice library
						} else { // we cannot process it
							String formattedValue=this.CELL_NOT_PROCESSABLE;
							String formula ="";
							String comment ="";
							String address = cellAddressAT.getValue();
							String sheetName = this.sheetName; 
							cells.add(new SpreadSheetCellDAO(formattedValue, comment, formula, address, sheetName) );
						}
					}
				}
				// else ignore (e.g. col)
			}
			}
		}
		// convert to array
		result = new SpreadSheetCellDAO[cells.size()];
		result = cells.toArray(result);
		// read all cells in row and create SpreadSheetCellDAOs
		this.nextBeingCalled=false;
		this.currentRow++;
		return result;
	}

}
