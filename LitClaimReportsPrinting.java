package com.legaledge.maestro.server.customds;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.apache.pdfbox.pdmodel.PDDocument;
//import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
//import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
//import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
//import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
//import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
//import org.apache.pdfbox.pdmodel.interactive.form.PDField;
//import org.apache.pdfbox.pdmodel.interactive.form.PDListBox;
//import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.util.Matrix;
import org.hibernate.Session;

import com.ibm.icu.text.SimpleDateFormat;
import com.isomorphic.datasource.DSRequest;
import com.isomorphic.datasource.DSResponse;
import com.legaledge.maestro.server.summary.LitClaimReports;
//import com.lowagie.text.PageSize;
//import com.lowagie.text.Rectangle;

public class LitClaimReportsPrinting {
	
	public static Logger logger = Logger.getLogger(LitClaimReportsPrinting.class);
	private List<LitClaimReports> reports;
	private String reportName;
	private Map requiredFieldsList;
	private int pageNumber=1;
	private String todayDate;


	public LitClaimReportsPrinting(PDRectangle letter, List<LitClaimReports> litClaimReports, String reportName, Map form) {
		super();
		this.reports=litClaimReports;
		this.reportName=reportName;	
		this.requiredFieldsList=form;
	}

	public void processReports(PDDocument printpdf, Session session, DSRequest dsRequest) throws IOException {

		SimpleDateFormat df = new SimpleDateFormat("dd/MM/yyyy  hh:mm:a");
		todayDate = df.format(new Date());

		for (LitClaimReports litClaim : reports) {
			
			PDPage page = new PDPage();
	
			PDPageContentStream stream = new PDPageContentStream(printpdf, page);
			stream.beginText();
			// stream.newLineAtOffset(35, 760);
			stream.setLeading(14.5f);
			//header
			addHeaderFooter(litClaim.getFileNumber(), litClaim.getCaseName(), stream);
			stream.setTextMatrix(Matrix.getTranslateInstance(40, 660));
			
			//fields 
			reportContent(dsRequest, session, litClaim, requiredFieldsList);
			stream.setTextMatrix(Matrix.getTranslateInstance(40, 650));
			// test
			stream.setFont(new PDType1Font(FontName.TIMES_ROMAN), 11);	
			
				
			/*____________LeftAlign 1__________________*/
			
			if(requiredFieldsList.get("court")!=null && (Boolean)requiredFieldsList.get("court")){
			stream.showText("Court "  +litClaim.getCourtName());
			}
			//2
			stream.newLine();
			stream.setTextMatrix(Matrix.getTranslateInstance(40, 630));
			if(requiredFieldsList.get("disposition")!=null && (Boolean)requiredFieldsList.get("disposition")){
			stream.showText("Disposition "  +litClaim.getDisposition());
			}
			//3
			stream.newLine();
			stream.setTextMatrix(Matrix.getTranslateInstance(40, 610));
			if (requiredFieldsList.get("witness") != null && (Boolean) requiredFieldsList.get("witness")) {
				stream.showText("Witness " +requiredFieldsList.get("witness").toString());
			}
			//4
			stream.newLine();
			stream.setTextMatrix(Matrix.getTranslateInstance(40, 590));
			if (requiredFieldsList.get("contacts") != null && (Boolean) requiredFieldsList.get("contacts")) {
				stream.showText("Contacts " + requiredFieldsList.get("contacts").toString());
			}
			//5
			stream.newLine();	
			stream.setTextMatrix(Matrix.getTranslateInstance(40, 570));
			if (requiredFieldsList.get("staffs") != null && (Boolean) requiredFieldsList.get("staffs")) {
				stream.showText("Staff " + litClaim.getStaffName());
			}
			//6
			stream.newLine();		
			stream.setTextMatrix(Matrix.getTranslateInstance(40, 550));
			if (requiredFieldsList.get("primaryPartyDOB") != null && (Boolean) requiredFieldsList.get("primaryPartyDOB")) {
				stream.showText("PrimaryPartyDOB " + requiredFieldsList.get("primaryPartyDOB").toString());
			}
			//7
			stream.newLine();	
			stream.setTextMatrix(Matrix.getTranslateInstance(40, 530));
			if (requiredFieldsList.get("dates") != null && (Boolean) requiredFieldsList.get("dates")) {
				stream.showText("dates " + litClaim.getDateFiled());
			}
			//8
			stream.newLine();	
			stream.setTextMatrix(Matrix.getTranslateInstance(40, 510));
			if (requiredFieldsList.get("status") != null && (Boolean) requiredFieldsList.get("status")) {
				stream.showText("Status " + litClaim.getStatus());
			}
			
			//9
			stream.newLine();	
			stream.setTextMatrix(Matrix.getTranslateInstance(40, 490));
			if (requiredFieldsList.get("Subject") != null && (Boolean) requiredFieldsList.get("Subject")) {
				stream.showText("Subject " + litClaim.getPriorityStatus());
			}
			
			//10
			stream.newLine();	
			stream.setTextMatrix(Matrix.getTranslateInstance(40, 470));
			if (requiredFieldsList.get("causeAndDepartment") != null && (Boolean) requiredFieldsList.get("causeAndDepartment")) {
				stream.showText("causeAndDepartment " + litClaim.getCauseDescription() + litClaim.getDepartment() );
			}
			
			//11
			stream.newLine();	
			stream.setTextMatrix(Matrix.getTranslateInstance(40, 450));
			if (requiredFieldsList.get("statusDescription") != null && (Boolean) requiredFieldsList.get("statusDescription")) {
				stream.showText("StatusDescription " + litClaim.getStatusDescription());
			}
			
			/* ______________Right Align____________________ */
			//1
			stream.setTextMatrix(Matrix.getTranslateInstance(300, 650));
			if (requiredFieldsList.get("evaluation") != null && (Boolean) requiredFieldsList.get("evaluation")) {
				stream.showText("Evaluation " +requiredFieldsList.get("evaluation").toString());
			}
			//2
			stream.newLine();	
			stream.setTextMatrix(Matrix.getTranslateInstance(300, 610));
			if (requiredFieldsList.get("abstract") != null && (Boolean) requiredFieldsList.get("abstract")) {
				stream.showText("abstract " +requiredFieldsList.get("abstract").toString());
			}
			//3
			stream.newLine();	
			stream.setTextMatrix(Matrix.getTranslateInstance(300, 630));
			if (requiredFieldsList.get("detailContactInfo") != null && (Boolean) requiredFieldsList.get("detailContactInfo")) {
				stream.showText("DetailContactInfo " +requiredFieldsList.get("detailContactInfo").toString());
			}
			
			//4
			stream.newLine();	
			stream.setTextMatrix(Matrix.getTranslateInstance(300, 590));
			if (requiredFieldsList.get("plaintiffDefendants") != null && (Boolean) requiredFieldsList.get("plaintiffDefendants")) {
				stream.showText("PlaintiffDefendants " +requiredFieldsList.get("plaintiffDefendants").toString());
			}
			//5
			stream.newLine();	
			stream.setTextMatrix(Matrix.getTranslateInstance(300, 570));
			if (requiredFieldsList.get("relatedParties") != null && (Boolean) requiredFieldsList.get("relatedParties")) {
				stream.showText("Related Parties " +requiredFieldsList.get("relatedParties").toString());
			}
			//6
			stream.newLine();	
			stream.setTextMatrix(Matrix.getTranslateInstance(300, 550));
			if (requiredFieldsList.get("categoryTypes") != null && (Boolean) requiredFieldsList.get("categoryTypes")) {
				stream.showText("Category & Type " +litClaim.getCategory());
			}
			
			stream.endText();
			printpdf.addPage(page);
			stream.close();
			}
		}
	
	
	private void addHeaderFooter(String fileNumber, String caseName, PDPageContentStream stream) throws IOException {

		stream.setFont(new PDType1Font(FontName.TIMES_BOLD), 13);

		stream.setTextMatrix(Matrix.getTranslateInstance(180, 760));
		stream.showText("City Of PittsBurgh, Department of Law");

		stream.setTextMatrix(Matrix.getTranslateInstance(240, 740));
		stream.showText(reportName);

		stream.setFont(new PDType1Font(FontName.TIMES_BOLD), 11);
		stream.newLine();
		stream.setTextMatrix(Matrix.getTranslateInstance(40, 720));
		stream.showText("File #                " + fileNumber);
		stream.newLine();
		stream.showText("Case Name     " + caseName);

		stream.newLine();
		stream.showText("______________________________________________________________________________________________________________________");
		
		
		
		stream.newLine();
		stream.showText("");
		stream.setTextMatrix(Matrix.getTranslateInstance(40, 40));
		stream.showText("______________________________________________________________________________________________________________________");

		stream.setFont(new PDType1Font(FontName.TIMES_ITALIC), 10);
		stream.newLine();
		stream.showText("Printed  "+todayDate);
		stream.newLine();
		stream.showText("City Of PittsBurgh, Department of Law");
		
		stream.setTextMatrix(Matrix.getTranslateInstance(550, 20));
		stream.showText("Page    "+pageNumber);
		++pageNumber;
		
	}
	
	public void reportContent(DSRequest dsRequest,Session session ,com.legaledge.maestro.server.summary.LitClaimReports litClaimReports, Map requiredFieldsList ) {
		Map values=dsRequest.getValues();
		Map litigationForm=(Map) values.get("litigationForm");
		
		
		/*if(litClaimReports != null){
			
		litClaimReports.getAttorney();
		litClaimReports.getAttorneyId();
		litClaimReports.getBoxLocation();
		litClaimReports.getCaseName(); //1 .
		litClaimReports.getCaseStatus(); //2 .
		litClaimReports.getCategory(); //3.
		litClaimReports.getCause(); //4
		litClaimReports.getCauseDescription(); //5
		litClaimReports.getComment();  //6
		litClaimReports.getDepartment(); //7
		litClaimReports.getDescription(); //8
		litClaimReports.getDisposition(); //9
		litClaimReports.getPriorityStatus();  //10
		litClaimReports.getStaffName();  //11
		litClaimReports.getStatus();  //12
		litClaimReports.getStatusDescription(); //13
		litClaimReports.getDispositionDescription();  //14
		}*/
		
	}
}