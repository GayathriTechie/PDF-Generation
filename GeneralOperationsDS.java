package com.legaledge.maestro.server.customds;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;

import org.apache.commons.collections.map.LinkedMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.Transaction;

import com.isomorphic.datasource.DSRequest;
import com.isomorphic.datasource.DSResponse;
import com.isomorphic.rpc.RPCManager;
import com.legaledge.harmony.integration.PersisterUtils;
import com.legaledge.harmony.model.MetadataCache;
import com.legaledge.harmony.model.PicklistValue;
import com.legaledge.harmony.pages.LoginRestrictedPage;
import com.legaledge.harmony.persistence.PersistenceSession;
import com.legaledge.harmony.persistence.Persister;
import com.legaledge.harmony.persistence.dao.CaseDAO;
import com.legaledge.harmony.tapestry5.pages.importfromncao.InsertNCAOData;
import com.legaledge.harmony.webservices.model.PendingImportPersonList;
import com.legaledge.harmony.webservices.model.SACWISPendingImportPersonList;
import com.legaledge.harmony.webservices.model.spillmanIntegrationModel;
import com.legaledge.maestro.server.businessrules.FinancialFlow;
import com.legaledge.maestro.server.dao.HibernateSessionFactory;
import com.legaledge.maestro.server.dao.generated.CurrentEntitiesDAO;
import com.legaledge.maestro.server.dao.utility.Utils;
import com.legaledge.maestro.server.model.report.AppearanceAttendeeDAO;
import com.legaledge.maestro.server.model.report.CaseStaffAssignmentDAO;
import com.legaledge.maestro.server.model.report.CaseToReferralAdjustmentDAO;
import com.legaledge.maestro.server.model.report.CaseToReferralPaymentDAO;
import com.legaledge.maestro.server.model.report.ClaimDAO;
import com.legaledge.maestro.server.model.report.CollectionCaseDAO;
import com.legaledge.maestro.server.model.report.ContemptCaseDAO;
import com.legaledge.maestro.server.model.report.ContractMatterDAO;
import com.legaledge.maestro.server.model.report.CriminalCaseDAO;
import com.legaledge.maestro.server.model.report.CriminalDefendantDAO;
import com.legaledge.maestro.server.model.report.CriminalStaffAssignmentDAO;
import com.legaledge.maestro.server.model.report.DefenderAssignmentDAO;
import com.legaledge.maestro.server.model.report.DelinquencyCaseDAO;
import com.legaledge.maestro.server.model.report.DelinquencyStaffAssignmentDAO;
import com.legaledge.maestro.server.model.report.DependencyCaseDAO;
import com.legaledge.maestro.server.model.report.EventToStaffDAO;
import com.legaledge.maestro.server.model.report.GeneralMatterDAO;
import com.legaledge.maestro.server.model.report.LitigationMatterDAO;
import com.legaledge.maestro.server.model.report.MentalHealthMatterDAO;
import com.legaledge.maestro.server.model.report.OrganizationDAO;
import com.legaledge.maestro.server.model.report.PersonDAO;
import com.legaledge.maestro.server.model.report.PreReferralAdjustmentDAO;
import com.legaledge.maestro.server.model.report.PreReferralPaymentDAO;
import com.legaledge.maestro.server.spillman.SpillmanImportService;
import com.legaledge.maestro.server.summary.CivilFutureAppearances;
import com.legaledge.maestro.server.summary.sacwis.SacwisParticipants;
import com.legaledge.maestro.shared.ApprovalStatusTypes;
import com.legaledge.maestro.utility.Conversion;
import com.legaledge.maestro.utility.StandaloneDAO;
import com.opencsv.CSVWriter;
import com.legaledge.maestro.server.summary.LitClaimReports;

public class GeneralOperationsDS {
	private static final Log logger = LogFactory.getLog(GeneralOperationsDS.class);
	public DSResponse fileAttachmentSearch(DSRequest req) throws Exception {
		Map values = req.getValues();
		Date fromDate = (Date) values.get("fromDate");
		Date toDate = (Date) values.get("toDate");
		String location = (String) values.get("location");
		Long createdBy = (Long) values.get("createdBy");
		String fileNumber = (String) values.get("fileNumber");
		String exhibit = (String) values.get("exhibit");

		DSResponse response = new DSResponse();
		Session session = StandaloneDAO.sessionOpen();

		Transaction tx = session.beginTransaction();
		try {
			StringBuffer hql = new StringBuffer(
					"from FileAttachmentCivil fa where fa.created between ? and ?");

			if (location != null) {
				hql.append(" and fa.location like '" + location + "%'");
			}

			if (fileNumber != null) {
				hql.append(" and fa.fileNumber like '" + fileNumber + "%'");
			}
			if (exhibit != null) {
				hql.append(" and fa.exhibit like '" + exhibit + "%'");
			}

			if (createdBy != null) {
				hql.append(" and fa.createdById=" + createdBy);
			}

			Query q = session.createQuery(hql.toString());
			q.setParameter(0, fromDate);
			q.setParameter(1, toDate);
			// q.setParameter(0, new Long(vals.get("parentId").toString()));
			List records = q.list();
			response.setData(records);
			tx.commit();
		} catch (Exception ex) {
			tx.rollback();
			ex.printStackTrace();
		} finally {
			HibernateSessionFactory.closeSession(session);
		}

		return response;
	}

	public DSResponse searchCaseForStaff(DSRequest req) throws Exception {
		Map values = req.getValues();
		Long staffId = new Long(values.get("staffId").toString());
		String role = (String) values.get("role");

		DSResponse response = new DSResponse();
		Session session = StandaloneDAO.sessionOpen();

		try {
			StringBuffer hql = new StringBuffer(
					"from CaseStaffAssignmentCivil ca where ca.staffId = ?  and ca.isOpen='true' and ca.role in ('Public Defender' ,'Assigned Attorney','Prosecutor') and ca.staffType in ('CaseStaffAssignment','DelinquencyStaffAssignment','DefenderAssignment','DefenderAssignmen')");

			Query q = session.createQuery(hql.toString());
			q.setParameter(0, staffId);
			List records = q.list();
			response.setData(records);
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			HibernateSessionFactory.closeSession(session);
		}

		return response;
	}

	public DSResponse reassignCases(DSRequest req) throws Exception {
		Map values = req.getValues();
		Long oldStaffId = new Long(values.get("oldStaffId").toString());	
		Long newStaffId = new Long(values.get("newStaffId").toString());
		List<String> caseIds = (List<String>) values.get("caseIds");
		Long currentPersonId = new Long(req.getHttpServletRequest().getSession().getAttribute("GWT.userId").toString());
		
		for(int i=0;i<caseIds.size();)
		{
			if((i+50) > caseIds.size())
			{
				List<String> splitRows1 = caseIds.subList(i, caseIds.size());
				processReassign(oldStaffId,newStaffId,currentPersonId, splitRows1);
				logger.info("Rows Processed : "+(i+splitRows1.size()));
				
			}
			else
			{
				List<String> splitRows = caseIds.subList(i, (i+50));
				processReassign(oldStaffId,newStaffId,currentPersonId, splitRows);
				logger.info("Rows Processed : "+i);
				
			}
			i=i+50;
		}

		DSResponse response = new DSResponse();
		
		return response;
	}

	private void processReassign(Long oldStaffId,Long newStaffId,Long currentPersonId, List<String> casesList) throws Exception
	{
		if(casesList != null && casesList.size() > 0)
		{
			StringBuffer cases=new StringBuffer();
			
			for(String caseStr:casesList)
			{
				if(cases.length() > 0)
					cases.append(";");
				
				cases.append(caseStr);
			}
			
			
			String sqlInsert = "exec hwe.reassign_case ?, ?, ?, ?";
			Connection connection=getCon();
			CallableStatement csInsert = connection.prepareCall(sqlInsert);
			
			csInsert.setString(1, cases.toString());
			csInsert.setLong(2, oldStaffId);
			csInsert.setLong(3, newStaffId);
			csInsert.setLong(4, currentPersonId);
			
			// Execute the query
			csInsert.execute();
			csInsert.close();
			connection.close();
			Thread.sleep(3000); // wait for a some time to complete back end process
			
		}
	}

	private Connection getCon() {
		Connection connection = null;
		try {
			InitialContext  ic = new InitialContext();
			  DataSource ds = (DataSource)ic.lookup( "java:/HarmonyDS" );
			  connection = ds.getConnection(); 

		} catch (NamingException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return connection;
	}

	public DSResponse reassignCasesOLD(DSRequest req) throws Exception {
		Map values = req.getValues();
		Long oldStaffId = new Long(values.get("oldStaffId").toString());
		Long newStaffId = new Long(values.get("newStaffId").toString());
		List<String> caseIds = (List<String>) values.get("caseIds");
		Long currentPersonId = new Long(req.getHttpServletRequest().getSession().getAttribute("GWT.personId").toString());
		//CaseStaff
		List<String> entityType=new ArrayList<>();
		List<com.legaledge.maestro.server.model.report.CaseStaffAssignmentDAO> caseStaff=new ArrayList<>();// other Case
		List<com.legaledge.maestro.server.model.report.DefenderAssignmentDAO> defenderAssignment=new ArrayList<>();// Criminal Case
		List<com.legaledge.maestro.server.model.report.DelinquencyStaffAssignmentDAO> delinquencyStaff=new ArrayList<>();// Delinquency Case


		DSResponse response = new DSResponse();
		Session session = StandaloneDAO.sessionOpen();
		if (caseIds != null && (!caseIds.isEmpty())) {


			Transaction tx = session.beginTransaction();
			try {

				PersonDAO oldStaff = (PersonDAO) session.get(PersonDAO.class,
						oldStaffId);
				PersonDAO newStaff = (PersonDAO) session.get(PersonDAO.class,
						newStaffId);

				Boolean isPrimary=false;
				Long existingRole=null;
				
				for (String strCaseId : caseIds) {
					Set<CaseStaffAssignmentDAO> recordsSet = null;

					StringTokenizer token = new StringTokenizer(strCaseId, " ");

					Long caseId = new Long(token.nextToken());
					String caseType = token.nextToken();
					String criminalDefendantId=token.nextToken();
					
					LocalDate localDateYesterday=LocalDate.now().minusDays(1);
					Date yesterday=java.sql.Date.valueOf(localDateYesterday);

					if (caseType.equals("LitigationMatter")) {
						LitigationMatterDAO caseDAO = (LitigationMatterDAO) session
								.get(LitigationMatterDAO.class, caseId);
						recordsSet = (Set<CaseStaffAssignmentDAO>) caseDAO
								.getCaseStaffAssignmentDAOs();
						CopyOnWriteArraySet<CaseStaffAssignmentDAO> records = new CopyOnWriteArraySet<CaseStaffAssignmentDAO>();
						records.addAll(recordsSet);
						if (records != null && (!records.isEmpty())) {
							for (CaseStaffAssignmentDAO staffDAO : records) {
								if (staffDAO.getPersonDAO().getId().longValue() == oldStaffId.longValue()&& staffDAO.getAssignmentEndDate() == null) {
									isPrimary=staffDAO.getIsPrimary();
									existingRole=staffDAO.getRole();
									staffDAO.setAssignmentEndDate(yesterday);
									staffDAO.setIsPrimary(Boolean.FALSE);
									Utils.setCreatedUpdatedBy(staffDAO, req, false);
									session.saveOrUpdate(staffDAO);								
								}
							}
							CaseStaffAssignmentDAO newStaffDAO = new CaseStaffAssignmentDAO();
							newStaffDAO.setAssignmentStartDate(new Date());
							newStaffDAO.setIsPrimary(isPrimary);
							newStaffDAO.setAssignmentEndDate(null);
							newStaffDAO.setPersonId(newStaffId);
							newStaffDAO.setRole(existingRole);
							Utils.setCreatedUpdatedBy(newStaffDAO, req, true);
							newStaffDAO.setLitigationMatterId(caseId);
							session.saveOrUpdate(newStaffDAO);
							
							entityType.add(caseType);
							caseStaff.add(newStaffDAO);
						}

					} else if (caseType.equals("Claim")) {
						ClaimDAO caseDAO = (ClaimDAO) session.get(
								ClaimDAO.class, caseId);
						recordsSet = (Set<CaseStaffAssignmentDAO>) caseDAO
								.getCaseStaffAssignmentDAOs();
						CopyOnWriteArraySet<CaseStaffAssignmentDAO> records = new CopyOnWriteArraySet<CaseStaffAssignmentDAO>();
						records.addAll(recordsSet);
						if(records!=null && !records.isEmpty()){
						 for (CaseStaffAssignmentDAO staffDAO : records) {
							if (staffDAO.getPersonDAO().getId().longValue() == oldStaffId.longValue() && staffDAO.getAssignmentEndDate() == null) {
								isPrimary=staffDAO.getIsPrimary();
								existingRole=staffDAO.getRole();
								staffDAO.setAssignmentEndDate(yesterday);
								staffDAO.setIsPrimary(Boolean.FALSE);
								Utils.setCreatedUpdatedBy(staffDAO, req, false);
								session.saveOrUpdate(staffDAO);							
							}
						 }
						    CaseStaffAssignmentDAO newStaffDAO = new CaseStaffAssignmentDAO();
							newStaffDAO.setAssignmentStartDate(new Date());
							newStaffDAO.setAssignmentEndDate(null);
							newStaffDAO.setPersonId(newStaffId);
							newStaffDAO.setIsPrimary(isPrimary);
							newStaffDAO.setRole(existingRole);
							newStaffDAO.setClaimId(caseId);
							Utils.setCreatedUpdatedBy(newStaffDAO, req, true);
							session.saveOrUpdate(newStaffDAO);
						}

					} else if (caseType.equals("GeneralMatter")) {
						GeneralMatterDAO caseDAO = (GeneralMatterDAO) session
								.get(GeneralMatterDAO.class, caseId);
						recordsSet = (Set<CaseStaffAssignmentDAO>) caseDAO
								.getCaseStaffAssignmentDAOs();
						CopyOnWriteArraySet<CaseStaffAssignmentDAO> records = new CopyOnWriteArraySet<CaseStaffAssignmentDAO>();
						records.addAll(recordsSet);
						if(records!=null && !records.isEmpty()){
						 for (CaseStaffAssignmentDAO staffDAO : records) {
							if (staffDAO.getPersonDAO().getId().longValue() == oldStaffId.longValue() && staffDAO.getAssignmentEndDate() == null) {
								isPrimary=staffDAO.getIsPrimary();
								existingRole=staffDAO.getRole();
								staffDAO.setAssignmentEndDate(yesterday);
								staffDAO.setIsPrimary(Boolean.FALSE);
								staffDAO.setGeneralMatterId(caseId);
								Utils.setCreatedUpdatedBy(staffDAO, req, false);
								session.saveOrUpdate(staffDAO);
							
							}
						}
						 	CaseStaffAssignmentDAO newStaffDAO = new CaseStaffAssignmentDAO();
							newStaffDAO.setAssignmentStartDate(new Date());
							newStaffDAO.setAssignmentEndDate(null);
							newStaffDAO.setIsPrimary(isPrimary);
							newStaffDAO.setPersonId(newStaffId);
							newStaffDAO.setRole(existingRole);
							newStaffDAO.setGeneralMatterId(caseId);
							Utils.setCreatedUpdatedBy(newStaffDAO, req, true);
							session.saveOrUpdate(newStaffDAO);
							
							entityType.add(caseType);
							caseStaff.add(newStaffDAO);
					  }
					} else if (caseType.equals("ContractMatter")) {
						ContractMatterDAO caseDAO = (ContractMatterDAO) session
								.get(ContractMatterDAO.class, caseId);
						recordsSet = (Set<CaseStaffAssignmentDAO>) caseDAO
								.getCaseStaffAssignmentDAOs();
						CopyOnWriteArraySet<CaseStaffAssignmentDAO> records = new CopyOnWriteArraySet<CaseStaffAssignmentDAO>();
						records.addAll(recordsSet);
						if(records!=null && !records.isEmpty()){
						 for (CaseStaffAssignmentDAO staffDAO : records) {
							if (staffDAO.getPersonDAO().getId().longValue() == oldStaffId.longValue() && staffDAO.getAssignmentEndDate() == null) {
								isPrimary=staffDAO.getIsPrimary();
								existingRole=staffDAO.getRole();
								staffDAO.setAssignmentEndDate(yesterday);
								staffDAO.setIsPrimary(Boolean.FALSE);
								Utils.setCreatedUpdatedBy(staffDAO, req, false);
								session.saveOrUpdate(staffDAO);								
							}
						 }
					   }
						CaseStaffAssignmentDAO newStaffDAO = new CaseStaffAssignmentDAO();
						newStaffDAO.setAssignmentStartDate(new Date());
						newStaffDAO.setAssignmentEndDate(null);
						newStaffDAO.setPersonId(newStaffId);
						newStaffDAO.setIsPrimary(isPrimary);
						newStaffDAO.setRole(existingRole);
						newStaffDAO.setContractMatterId(caseId);
						Utils.setCreatedUpdatedBy(newStaffDAO, req, true);
						session.saveOrUpdate(newStaffDAO);
						
						entityType.add(caseType);
						caseStaff.add(newStaffDAO);
					} 
					else if (caseType.equals("CriminalCase")) {
					  CriminalCaseDAO caseDAO = (CriminalCaseDAO) session.get(CriminalCaseDAO.class, caseId);
					  if(criminalDefendantId!=null && !criminalDefendantId.equals("null")){
						CriminalDefendantDAO criminalDefendant = (CriminalDefendantDAO) session.get(CriminalDefendantDAO.class, new Long(criminalDefendantId));					
						if(criminalDefendant != null )
						{
								Set<DefenderAssignmentDAO> defAssignments = criminalDefendant.getDefenderAssignmentDAOs();							
								if(defAssignments != null && (!defAssignments.isEmpty()))
								{
									CopyOnWriteArraySet<DefenderAssignmentDAO> recordsDefAssignments = new CopyOnWriteArraySet<DefenderAssignmentDAO>();
									recordsDefAssignments.addAll(defAssignments);
									if(recordsDefAssignments!=null && !recordsDefAssignments.isEmpty()){
									 for(DefenderAssignmentDAO defAssignment:recordsDefAssignments)
									 {
										if(defAssignment.getPersonDAO().getId().longValue() == oldStaffId.longValue() && defAssignment.getAssignmentEndDate() == null) {
											isPrimary=defAssignment.getIsPrimary();
											existingRole=defAssignment.getType();
											defAssignment.setIsPrimary(Boolean.FALSE);
											defAssignment.setAssignmentEndDate(yesterday);
											Utils.setCreatedUpdatedBy(defAssignment, req, false);
											session.saveOrUpdate(defAssignment);
										}
									 }
									 	DefenderAssignmentDAO newDefAssignment=new DefenderAssignmentDAO();
										newDefAssignment.setIsPrimary(isPrimary);
										newDefAssignment.setAssignmentStartDate(new Date());
										newDefAssignment.setPersonId(newStaffId);
										newDefAssignment.setType(existingRole);
										newDefAssignment.setCriminalDefendantDAO(criminalDefendant);
										Utils.setCreatedUpdatedBy(newDefAssignment, req, true);
										session.saveOrUpdate(newDefAssignment);
										defenderAssignment.add(newDefAssignment);
									}
								}				
						}
					  }
					  //Staffing
						Set<CriminalStaffAssignmentDAO> recordsSetCriminalStaff = (Set<CriminalStaffAssignmentDAO>) caseDAO.getCriminalStaffAssignmentDAOs();
						CopyOnWriteArraySet<CriminalStaffAssignmentDAO> recordsCriminalStaff = new CopyOnWriteArraySet<CriminalStaffAssignmentDAO>();
						recordsCriminalStaff.addAll(recordsSetCriminalStaff);
						if(recordsCriminalStaff!=null && !recordsCriminalStaff.isEmpty()){
						 for (CriminalStaffAssignmentDAO staffDAO : recordsCriminalStaff) {
							if (staffDAO.getPersonDAO().getId().longValue() == oldStaffId.longValue()&& staffDAO.getAssignmentEndDate() == null) {
								isPrimary=staffDAO.getIsPrimary();
								existingRole=staffDAO.getRole();
								staffDAO.setAssignmentEndDate(yesterday);
								staffDAO.setIsPrimary(Boolean.FALSE);
								Utils.setCreatedUpdatedBy(staffDAO, req, false);
								session.saveOrUpdate(staffDAO);									
							}
						}
						    CriminalStaffAssignmentDAO newStaffDAO = new CriminalStaffAssignmentDAO();
							newStaffDAO.setAssignmentStartDate(new Date());
							newStaffDAO.setAssignmentEndDate(null);
							newStaffDAO.setPersonId(newStaffId);
							newStaffDAO.setCriminalCaseId(caseId);
							newStaffDAO.setIsPrimary(isPrimary);
							newStaffDAO.setRole(existingRole);
							Utils.setCreatedUpdatedBy(newStaffDAO, req, true);
							session.saveOrUpdate(newStaffDAO);
					  }
					}
					 else if (caseType.equals("DelinquencyCase")) {
							DelinquencyCaseDAO caseDAO = (DelinquencyCaseDAO) session.get(
									DelinquencyCaseDAO.class, caseId);
							Set<DelinquencyStaffAssignmentDAO> recordsDelinquencySet=(Set<DelinquencyStaffAssignmentDAO>) caseDAO.getDelinquencyStaffAssignmentDAOs();
							CopyOnWriteArraySet<DelinquencyStaffAssignmentDAO> records = new CopyOnWriteArraySet<DelinquencyStaffAssignmentDAO>();
							records.addAll(recordsDelinquencySet);
							if(records!=null && !records.isEmpty()){
							 for (DelinquencyStaffAssignmentDAO staffDAO : records) {
								if (staffDAO.getPersonDAO().getId().longValue() == oldStaffId.longValue()&& staffDAO.getAssignmentEndDate() == null) {
									isPrimary=staffDAO.getIsPrimary();
									existingRole=staffDAO.getRole();
									staffDAO.setAssignmentEndDate(yesterday);
									staffDAO.setIsPrimary(Boolean.FALSE);
									Utils.setCreatedUpdatedBy(staffDAO, req, false);
									session.saveOrUpdate(staffDAO);									
								}
							 }
							    DelinquencyStaffAssignmentDAO newStaffDAO = new DelinquencyStaffAssignmentDAO();
								newStaffDAO.setAssignmentStartDate(new Date());
								newStaffDAO.setAssignmentEndDate(null);
								newStaffDAO.setPersonId(newStaffId);
								newStaffDAO.setIsPrimary(isPrimary);
								newStaffDAO.setRole(existingRole);
								newStaffDAO.setDelinquencyCaseId(caseId);
								Utils.setCreatedUpdatedBy(newStaffDAO, req, true);
								session.saveOrUpdate(newStaffDAO);
								
								delinquencyStaff.add(newStaffDAO);
						  }
						}
					 else if (caseType.equals("DependencyCase")) {
						 DependencyCaseDAO caseDAO = (DependencyCaseDAO) session.get(DependencyCaseDAO.class, caseId);
							recordsSet = (Set<CaseStaffAssignmentDAO>) caseDAO.getCaseStaffAssignmentDAOs();
							CopyOnWriteArraySet<CaseStaffAssignmentDAO> records = new CopyOnWriteArraySet<CaseStaffAssignmentDAO>();
							records.addAll(recordsSet);
							if(records!=null && !records.isEmpty()){
							 for (CaseStaffAssignmentDAO staffDAO : records) {
								if (staffDAO.getPersonDAO().getId().longValue() == oldStaffId.longValue() && staffDAO.getAssignmentEndDate() == null) {
									isPrimary=staffDAO.getIsPrimary();
									existingRole=staffDAO.getRole();
									staffDAO.setAssignmentEndDate(yesterday);
									staffDAO.setIsPrimary(Boolean.FALSE);
									Utils.setCreatedUpdatedBy(staffDAO, req, false);
									session.saveOrUpdate(staffDAO);								
								}
							 }
						   }
							CaseStaffAssignmentDAO newStaffDAO = new CaseStaffAssignmentDAO();
							newStaffDAO.setAssignmentStartDate(new Date());
							newStaffDAO.setAssignmentEndDate(null);
							newStaffDAO.setPersonId(newStaffId);
							newStaffDAO.setIsPrimary(isPrimary);
							newStaffDAO.setRole(existingRole);
							newStaffDAO.setDependencyCaseId(caseId);
							Utils.setCreatedUpdatedBy(newStaffDAO, req, true);
							session.saveOrUpdate(newStaffDAO);
							
							entityType.add(caseType);
							caseStaff.add(newStaffDAO);
						} 
					 else if (caseType.equals("MentalHealthMatter")) {
						 MentalHealthMatterDAO caseDAO = (MentalHealthMatterDAO) session
									.get(MentalHealthMatterDAO.class, caseId);
							recordsSet = (Set<CaseStaffAssignmentDAO>) caseDAO
									.getCaseStaffAssignmentDAOs();
							CopyOnWriteArraySet<CaseStaffAssignmentDAO> records = new CopyOnWriteArraySet<CaseStaffAssignmentDAO>();
							records.addAll(recordsSet);
							if(records!=null && !records.isEmpty()){
							 for (CaseStaffAssignmentDAO staffDAO : records) {
								if (staffDAO.getPersonDAO().getId().longValue() == oldStaffId.longValue() && staffDAO.getAssignmentEndDate() == null) {
									isPrimary=staffDAO.getIsPrimary();
									existingRole=staffDAO.getRole();
									staffDAO.setAssignmentEndDate(yesterday);
									staffDAO.setIsPrimary(Boolean.FALSE);
									Utils.setCreatedUpdatedBy(staffDAO, req, false);
									session.saveOrUpdate(staffDAO);								
								}
							 }
						   }
							CaseStaffAssignmentDAO newStaffDAO = new CaseStaffAssignmentDAO();
							newStaffDAO.setAssignmentStartDate(new Date());
							newStaffDAO.setAssignmentEndDate(null);
							newStaffDAO.setPersonId(newStaffId);
							newStaffDAO.setIsPrimary(isPrimary);
							newStaffDAO.setRole(existingRole);
							newStaffDAO.setMentalHealthMatterId(caseId);
							Utils.setCreatedUpdatedBy(newStaffDAO, req, true);
							session.saveOrUpdate(newStaffDAO);
							
							entityType.add(caseType);
							caseStaff.add(newStaffDAO);
						} 
					 else if (caseType.equals("ContemptCase")) {
						 ContemptCaseDAO caseDAO = (ContemptCaseDAO) session
									.get(ContemptCaseDAO.class, caseId);
							recordsSet = (Set<CaseStaffAssignmentDAO>) caseDAO
									.getCaseStaffAssignmentDAOs();
							CopyOnWriteArraySet<CaseStaffAssignmentDAO> records = new CopyOnWriteArraySet<CaseStaffAssignmentDAO>();
							records.addAll(recordsSet);
							if(records!=null && !records.isEmpty()){
							 for (CaseStaffAssignmentDAO staffDAO : records) {
								if (staffDAO.getPersonDAO().getId().longValue() == oldStaffId.longValue() && staffDAO.getAssignmentEndDate() == null) {
									isPrimary=staffDAO.getIsPrimary();
									existingRole=staffDAO.getRole();
									staffDAO.setAssignmentEndDate(yesterday);
									staffDAO.setIsPrimary(Boolean.FALSE);
									Utils.setCreatedUpdatedBy(staffDAO, req, false);
									session.saveOrUpdate(staffDAO);								
								}
							 }
						   }
							CaseStaffAssignmentDAO newStaffDAO = new CaseStaffAssignmentDAO();
							newStaffDAO.setAssignmentStartDate(new Date());
							newStaffDAO.setAssignmentEndDate(null);
							newStaffDAO.setPersonId(newStaffId);
							newStaffDAO.setIsPrimary(isPrimary);
							newStaffDAO.setRole(existingRole);
							newStaffDAO.setContemptCaseId(caseId);
							Utils.setCreatedUpdatedBy(newStaffDAO, req, true);
							session.saveOrUpdate(newStaffDAO);
							
							entityType.add(caseType);
							caseStaff.add(newStaffDAO);
							
						} 
					else {
						CollectionCaseDAO caseDAO = (CollectionCaseDAO) session
								.get(CollectionCaseDAO.class, caseId);
						recordsSet = (Set<CaseStaffAssignmentDAO>) caseDAO
								.getCaseStaffAssignmentDAOs();
						CopyOnWriteArraySet<CaseStaffAssignmentDAO> records = new CopyOnWriteArraySet<CaseStaffAssignmentDAO>();
						records.addAll(recordsSet);
						if(records!=null && !records.isEmpty()){
						  for (CaseStaffAssignmentDAO staffDAO : records) {
							if (staffDAO.getPersonDAO().getId().longValue() == oldStaffId.longValue()&& staffDAO.getAssignmentEndDate() == null) {
								isPrimary=staffDAO.getIsPrimary();
								existingRole=staffDAO.getRole();
								staffDAO.setAssignmentEndDate(yesterday);
								staffDAO.setIsPrimary(Boolean.FALSE);
								Utils.setCreatedUpdatedBy(staffDAO, req, false);
								session.saveOrUpdate(staffDAO);								
							}
						 }
						    CaseStaffAssignmentDAO newStaffDAO = new CaseStaffAssignmentDAO();
							newStaffDAO.setAssignmentStartDate(new Date());
							newStaffDAO.setAssignmentEndDate(null);
							newStaffDAO.setPersonId(newStaffId);
							newStaffDAO.setIsPrimary(isPrimary);
							newStaffDAO.setRole(existingRole);
							newStaffDAO.setCollectionCaseId(caseId);
							Utils.setCreatedUpdatedBy(newStaffDAO, req, true);
							session.saveOrUpdate(newStaffDAO);
						}
						
					}
					StringBuffer hql = new StringBuffer(
							"from CivilFutureAppearances fa where fa.attendeePersonId="+ oldStaffId +" and fa.caseId="+caseId  );
					if(caseType.equals("CriminalCase") && criminalDefendantId!=null && !criminalDefendantId.equals("null")){
						hql.append(" and fa.defendantId="+criminalDefendantId);
					}

					Query q = session.createQuery(hql.toString());
					// q.setParameter(0, new Long(vals.get("parentId").toString()));
					List<CivilFutureAppearances> appearances = q.list();

					if (appearances != null && (!appearances.isEmpty())) {
						for (CivilFutureAppearances appearance : appearances) {
							AppearanceAttendeeDAO attendee = (AppearanceAttendeeDAO) session
									.get(AppearanceAttendeeDAO.class,
											appearance.getAttendeeId());

							if (attendee != null && attendee.getAppearanceDAO() != null && attendee.getAppearanceDAO().getStartDate() != null) {
								
								SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy");
								if(sdf.parse(sdf.format(attendee.getAppearanceDAO().getStartDate())).compareTo(sdf.parse(sdf.format(attendee.getAppearanceDAO().getStartDate()))) >=0 )
								{
									if(attendee.getAppearanceDAO().getOutcome() == null)
									{
										attendee.setPersonId(newStaffId);
										Utils.setCreatedUpdatedBy(attendee, req, false);
										session.saveOrUpdate(attendee);
									}
								}
							}

						}
					}

					if (oldStaff.getEventToStaffDAOs() != null
							&& (!oldStaff.getEventToStaffDAOs().isEmpty())) {
						CopyOnWriteArraySet<EventToStaffDAO> eventToStaffDAOs = new CopyOnWriteArraySet<EventToStaffDAO>();
						eventToStaffDAOs.addAll(oldStaff.getEventToStaffDAOs());

						for (EventToStaffDAO eventToStaffDAO : eventToStaffDAOs) {
							if(eventToStaffDAO.getEventDAO() != null && eventToStaffDAO.getEventDAO().getCaseId()!=null && eventToStaffDAO.getEventDAO().getCaseId().longValue()==caseId.longValue() &&  eventToStaffDAO.getEventDAO().getEndDate() == null && eventToStaffDAO.getEventDAO().getCompletionDate()==null)
							{
								eventToStaffDAO.setPersonId(newStaffId);
								Utils.setCreatedUpdatedBy(eventToStaffDAO, req, false);
								session.saveOrUpdate(eventToStaffDAO);
							}
						}

					}
				}				
				tx.commit();
				if(MetadataCache.isSetting("ExchangeExport", "true") && !MetadataCache.isSetting("exchange.appt.sender.type", "GOOGLE") ){
					outlookEntries(defenderAssignment,delinquencyStaff,caseStaff,entityType,currentPersonId,req.getHttpServletRequest().getSession());
				}
				response.setStatus(DSResponse.STATUS_SUCCESS);
			} catch (Exception ex) {
				response.setStatus(DSResponse.STATUS_FAILURE);
				tx.rollback();
				ex.printStackTrace();
			} finally {
				HibernateSessionFactory.closeSession(session);
			}
		
		}

		return response;
	}
	
	public DSResponse searchPeopleAdhoc(DSRequest req) throws Exception {
		Map values = req.getValues();
		//,middleName,lastName,contact,status,city, state
		
		String firstName = (String) values.get("firstName");
		String middleName = (String) values.get("middleName");
		String lastName = (String) values.get("lastName");
		String contact = (String) values.get("contact");
		String status = (String) values.get("status");
		String city = (String) values.get("city");
		String state = (String) values.get("state");
		

		DSResponse response = new DSResponse();
		Session session = StandaloneDAO.sessionOpen();

		Transaction tx = session.beginTransaction();
		try {
			StringBuffer hql = new StringBuffer(
					"from MailInformation mi ");
			
			if(firstName != null && (!firstName.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and mi.firstName='"+firstName+"'");
				}
				else
				{
					hql.append(" where mi.firstName='"+firstName+"'");
				}
			}
			
			if(middleName != null && (!middleName.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and mi.middleName='"+middleName+"'");
				}
				else
				{
					hql.append(" where mi.middleName='"+middleName+"'");
				}
			}
			
			if(lastName != null && (!lastName.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and mi.lastName='"+lastName+"'");
				}
				else
				{
					hql.append(" where mi.lastName='"+lastName+"'");
				}
			}
			
			if(contact != null && (!contact.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and mi.contact='"+contact+"'");
				}
				else
				{
					hql.append(" where mi.contact='"+contact+"'");
				}
			}
			
			if(status != null && (!status.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and mi.status='"+status+"'");
				}
				else
				{
					hql.append(" where mi.status='"+status+"'");
				}
			}
			
			if(city != null && (!city.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and mi.city='"+city+"'");
				}
				else
				{
					hql.append(" where mi.city='"+city+"'");
				}
			}
			
			if(state != null && (!state.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and mi.state='"+state+"'");
				}
				else
				{
					hql.append(" where mi.state='"+state+"'");
				}
			}
			Query q = session.createQuery(hql.toString());
			List records = q.list();
			
			response.setData(records);
			tx.commit();
		} catch (Exception ex) {
			tx.rollback();
			ex.printStackTrace();
		} finally {
			HibernateSessionFactory.closeSession(session);
		}

		return response;
	}
	
	public DSResponse searchExpenseAdhoc(DSRequest req) throws Exception {
		Map values = req.getValues();
		Long accountId = (Long) values.get("accountNumber");
		String checkNumber = (String) values.get("checkNumber");
		Long payeeId = (Long) values.get("payeeId");
		String type = (String) values.get("type");
		String matterName = (String) values.get("matterName");
		String matterNumber = (String) values.get("matterNumber");
		Map dateRange=(Map) values.get("DateRange");

		DSResponse response = new DSResponse();
		Session session = StandaloneDAO.sessionOpen();

		Transaction tx = session.beginTransaction();
		try {
			StringBuffer hql = new StringBuffer(
					"from CheckRequestToCaseCivil cr ");
			
			if(accountId != null)
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and cr.accountId="+accountId+"");
				}
				else
				{
					hql.append(" where cr.accountId="+accountId+"");
				}
			}
			
			if(checkNumber != null && (!checkNumber.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and cr.checkNumber like '"+checkNumber+"%'");
				}
				else
				{
					hql.append(" where cr.checkNumber like '"+checkNumber+"%'");
				}
			}
			
			if(payeeId != null)
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and cr.payeeId='"+payeeId+"'");
				}
				else
				{
					hql.append(" where cr.payeeId='"+payeeId+"'");
				}
			}
			
			if(type != null && (!type.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and cr.type='"+type+"'");
				}
				else
				{
					hql.append(" where cr.type='"+type+"'");
				}
			}
			
			if(matterName != null && (!matterName.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and cr.matterName like '"+matterName+"%'");
				}
				else
				{
					hql.append(" where cr.matterName like '"+matterName+"%'");
				}
			}
			
			if(matterNumber != null && (!matterNumber.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and cr.matterNumber like '"+matterNumber+"%'");
				}
				else
				{
					hql.append(" where cr.matterNumber like '"+matterNumber+"%'");
				}
			}
			
			if(dateRange != null)
			{
				Date startDate=(Date)dateRange.get("start");
				Date endDate=(Date)dateRange.get("end");
				
				if(startDate != null && endDate != null)
				{
					if(hql.toString().contains("where"))
					{
						hql.append(" and cr.invoiceDate between ? and ?");
					}
					else
					{
						hql.append(" where cr.invoiceDate between ? and ?");
					}
				}
			}
			
			
			Query q = session.createQuery(hql.toString());
			
			if(dateRange != null)
			{
				Date startDate=(Date)dateRange.get("start");
				Date endDate=(Date)dateRange.get("end");
				
				if(startDate != null && endDate != null)
				{
					q.setParameter(0, startDate);
					q.setParameter(1, endDate);
				}
			}
			
			List records = q.list();
			
			response.setData(records);
			tx.commit();
		} catch (Exception ex) {
			tx.rollback();
			ex.printStackTrace();
		} finally {
			HibernateSessionFactory.closeSession(session);
		}

		return response;
	}
	
	public DSResponse litigationMatterAdhocSearch(DSRequest req) throws Exception {
		Map values = req.getValues();
		
		Long staffId = (Long) values.get("staffId");
		Long subType = (Long) values.get("subType");
		String matterName = (String) values.get("matterName");
		Long client = (Long) values.get("client");
		Long courtName = (Long) values.get("courtName");
		String courtNumber = (String) values.get("courtNumber");
		Long type = (Long) values.get("type");
		String section = (String) values.get("section");
		Long incidentCode=(Long) values.get("incidentCode");
		//Object dateRange= values.get("DateRange");
		
		//dateFiledRange dateClosedRange
		Map dateFiledRange=(Map)values.get("dateFiledRange");
		Map dateClosedRange=(Map)values.get("dateClosedRange");
	
		String disposition = (String) values.get("disposition");
		String fee = (String) values.get("fee");
		String caseType = (String) values.get("caseType");
		String status = (String) values.get("status");
		String entityName = (String) values.get("entityName");
		

		DSResponse response = new DSResponse();
		Session session = StandaloneDAO.sessionOpen();

		Transaction tx = session.beginTransaction();
		try {
			StringBuffer hql = new StringBuffer(
					"from LitigationMatterAdhoc lm ");
			
			if(staffId != null)
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and lm.staffId="+staffId+"");
				}
				else
				{
					hql.append(" where lm.staffId="+staffId+"");
				}
			}
			
			if(subType != null)
			{
				if(hql.toString().contains("where"))
				{
					if(MetadataCache.isSetting("Build.Barrack", "true"))
					{
						hql.append(" and lm.subTypeId = "+subType+"");
					}
					else
					{
						hql.append(" and lm.ourRoleId = "+subType+"");
					}
				}
				else
				{
					if(MetadataCache.isSetting("Build.Barrack", "true"))
					{
						hql.append(" where lm.subTypeId = "+subType+"");
					}
					else
					{
						hql.append(" where lm.subTypeId = "+subType+"");
					}
				}
			}
			
			if(matterName != null && (!matterName.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and lm.matterName like '"+matterName+"%'");
				}
				else
				{
					hql.append(" where lm.matterName like '"+matterName+"%'");
				}
			}
			
			if(section != null && (!section.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and lm.section = '"+section+"'");
				}
				else
				{
					hql.append(" where lm.section = '"+section+"'");
				}
			}
			
			if(client != null)
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and lm.clientId = "+client+"");
				}
				else
				{
					hql.append(" where lm.clientId = "+client+"");
				}
			}
			
			if(courtName != null)
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and lm.courtId = "+courtName+"");
				}
				else
				{
					hql.append(" where lm.courtId = "+courtName+"");
				}
			}
			
			if(courtNumber != null && (!courtNumber.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and lm.courtNumber like '"+courtNumber+"%'");
				}
				else
				{
					hql.append(" where lm.courtNumber like '"+courtNumber+"%'");
				}
			}
			
			if(disposition != null && (!disposition.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and lm.disposition like '"+disposition+"%'");
				}
				else
				{
					hql.append(" where lm.disposition like '"+disposition+"%'");
				}
			}
			
			if(fee != null && (!fee.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and lm.fee like '"+fee+"%'");
				}
				else
				{
					hql.append(" where lm.fee like '"+fee+"%'");
				}
			}
			
			if(caseType != null && (!caseType.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and lm.caseType like '"+caseType+"%'");
				}
				else
				{
					hql.append(" where lm.caseType like '"+caseType+"%'");
				}
			}
			
			if(status != null && (!status.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and lm.status like '"+status+"%'");
				}
				else
				{
					hql.append(" where lm.status like '"+status+"%'");
				}
			}
			
			if(type != null)
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and lm.caseTypeId = "+type+"");
				}
				else
				{
					hql.append(" where lm.caseTypeId = "+type+"");
				}
			}
			if(incidentCode != null)
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and lm.incidentCodeId = "+incidentCode+"");
				}
				else
				{
					hql.append(" where lm.incidentCodeId = "+incidentCode+"");
				}
			}
			
			if(entityName != null && (!entityName.trim().equals("")))
			{
				if(hql.toString().contains("where"))
				{
					hql.append(" and lm.entityName = '"+entityName+"'");
				}
				else
				{
					hql.append(" where lm.entityName = '"+entityName+"'");
				}
			}
			
			if(dateFiledRange != null)
			{
				Date startDate=(Date)dateFiledRange.get("start");
				Date endDate=(Date)dateFiledRange.get("end");
				
				if(startDate != null && endDate != null)
				{
					if(hql.toString().contains("where"))
					{
						hql.append(" and lm.startDate between :filedStartDate and :filedEndDate");
					}
					else
					{
						hql.append(" where lm.startDate between :filedStartDate and :filedEndDate");
					}
				}
			}
			
			if(dateClosedRange != null)
			{
				Date startDate=(Date)dateClosedRange.get("start");
				Date endDate=(Date)dateClosedRange.get("end");
				
				if(startDate != null && endDate != null)
				{
					if(hql.toString().contains("where"))
					{
						hql.append(" and lm.dateClosed between :closedStartDate and :closedEndDate");
					}
					else
					{
						hql.append(" where lm.dateClosed between :closedStartDate and :closedEndDate");
					}
				}
			}
			
			hql.append(" order by lm.startDate");
			Query q = session.createQuery(hql.toString());
			SimpleDateFormat tempFormatShort=new SimpleDateFormat("yyyy-MM-dd");
			SimpleDateFormat tempFormatLong=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

			if(dateFiledRange != null)
			{
				Date startDate=(Date)dateFiledRange.get("start");
				Date endDate=(Date)dateFiledRange.get("end");
				
				if(startDate != null && endDate != null)
				{
				
					Date startDateFormatted=tempFormatLong.parse(tempFormatShort.format(startDate)+" 00:00:00");
					Date endDateFormatted=tempFormatLong.parse(tempFormatShort.format(endDate)+" 23:59:59");
					
					q.setParameter("filedStartDate", startDateFormatted);
					q.setParameter("filedEndDate", endDateFormatted);
				}
			}
			
			if(dateClosedRange != null)
			{
				Date startDate=(Date)dateClosedRange.get("start");
				Date endDate=(Date)dateClosedRange.get("end");
				
				if(startDate != null && endDate != null)
				{
					Date startDateFormatted=tempFormatLong.parse(tempFormatShort.format(startDate)+" 00:00:00");
					Date endDateFormatted=tempFormatLong.parse(tempFormatShort.format(endDate)+" 23:59:59");
					
					q.setParameter("closedStartDate", startDateFormatted);
					q.setParameter("closedEndDate", endDateFormatted);
				}
			}
			
			List records = q.list();
			
			response.setData(records);
			tx.commit();
		} catch (Exception ex) {
			tx.rollback();
			ex.printStackTrace();
		} finally {
			HibernateSessionFactory.closeSession(session);
		}

		return response;
	}
	
	public DSResponse getHasReserveRecords(DSRequest dsRequest) throws Exception { 
		
		Map values=dsRequest.getValues();
		Map<String,Boolean> result=new LinkedHashMap<String,Boolean>();
		DSResponse response=new DSResponse();
		if(values.get("caseId") != null)
		{
			Session session = StandaloneDAO.sessionOpen();
			Transaction tx = session.beginTransaction();
			try {
				//CaseToReserveDAO
				Query query=session.createQuery("from CaseToReserveDAO cr where (cr.litigationMatterDAO.id=? or cr.generalMatterDAO.id=? or cr.claimDAO.id=?)");
				query.setParameter(0, values.get("caseId"));
				query.setParameter(1, values.get("caseId"));
				query.setParameter(2, values.get("caseId"));
				List list=query.list();
				
				if(list != null && (!list.isEmpty()))
				{
					result.put("reserve", Boolean.TRUE);
				}
				else
				{
					result.put("reserve", Boolean.FALSE);
				}
				response.setData(result);
				tx.commit();
			}catch (Exception e) {
				e.printStackTrace();
				tx.rollback();
			} finally {
				HibernateSessionFactory.closeSession(session);
			}
		}
		
		return response; 
		
	}

	public DSResponse getInvestigationCount(DSRequest dsRequest) throws Exception { 
		
		Map values=dsRequest.getValues();
		Map<String,Integer> result=new LinkedHashMap<String,Integer>();
		DSResponse response=new DSResponse();
		if(values.get("personId") != null)
		{
			int count = CaseDAO.countOfInvestigationRequestsAssignedToLoggedInPerson(PersisterUtils.getPersister(), new Long(values.get("personId").toString()));
			result.put("InvestigationCount", count);
			response.setData(result);
		}
		
		return response; 
	}
	
	public DSResponse getOrganizationToFeeArrangement(DSRequest dsRequest) throws Exception { 
		
		Map values=dsRequest.getValues();
		Map<String,String> result=new LinkedHashMap<String,String>();
		DSResponse response=new DSResponse();
		if(values.get("id") != null)
		{
			Session session = StandaloneDAO.sessionOpen();
			try {
				OrganizationDAO organizationDAO=(OrganizationDAO)session.get(OrganizationDAO.class, new Long(values.get("id").toString()));
				if(organizationDAO.getOrganizationToFeeArrangementDAOs() != null && (!organizationDAO.getOrganizationToFeeArrangementDAOs().isEmpty()))
				{
					result.put("FEES", "EXISTS");
				}
				else
				{
					result.put("FEES", "NOTEXISTS");
				}
				
			}catch (Exception e) {
				result.put("FEES", e.getMessage());
				e.printStackTrace();
			} finally {
				HibernateSessionFactory.closeSession(session);
			}
		}
		response.setData(result);
		return response; 
	}
	
	public DSResponse onDeletePreReferralPayments(DSRequest dsRequest) throws Exception {
		logger.debug("called updateAssignedAmountPreReferralAjustments");
		Map values=dsRequest.getValues();
		Map<String,String> result=new LinkedHashMap<String,String>();
		DSResponse response=new DSResponse();
		if(values.get("id") != null)
		{
			Session session = StandaloneDAO.sessionOpen();
			Transaction tx=session.beginTransaction();
			try {
				CollectionCaseDAO collectionCaseDAO=(CollectionCaseDAO)session.get(CollectionCaseDAO.class, new Long(values.get("id").toString()));
				float assignedAmount =  FinancialFlow.initialPrincipalBalance(collectionCaseDAO);
				collectionCaseDAO.setAssignedAmount(String.valueOf(assignedAmount));
				session.saveOrUpdate(collectionCaseDAO);
				tx.commit();	
				logger.debug("Collection Case assigned amount changed successfully");
				result.put("RESULT", "SUCCESS");
			}catch (Exception e) {
				tx.rollback();
				result.put("ERROR", e.getMessage());
				e.printStackTrace();
			} finally {
				HibernateSessionFactory.closeSession(session);
			}
		}
		response.setData(result);
		return response; 
	}
	
	public DSResponse updateAssignedAmountPreReferralPayments(DSRequest dsRequest) throws Exception { 
		logger.debug("called updateAssignedAmountPreReferralPayments");
		Map values=dsRequest.getValues();
		Map<String,String> result=new LinkedHashMap<String,String>();
		DSResponse response=new DSResponse();
		if(values.get("id") != null)
		{
			Session session = StandaloneDAO.sessionOpen();
			Transaction tx=session.beginTransaction();
			try {
				PreReferralPaymentDAO preReferral=(PreReferralPaymentDAO)session.get(PreReferralPaymentDAO.class, new Long(values.get("id").toString()));
				Set<CaseToReferralPaymentDAO> preReferralObjects = (Set<CaseToReferralPaymentDAO>) preReferral.getCaseToReferralPaymentDAOs();
				if(preReferralObjects != null && preReferralObjects.size() > 0)
				{
					logger.debug("Found CaseToReferralPaymentDAO");
					
					for(CaseToReferralPaymentDAO relToCase:preReferralObjects)
					{
						if(preReferral.getAmount() != null)
						{
							logger.debug("Found preReferral.getAmount() : "+preReferral.getAmount());
							logger.debug("Updating collection case assigned amount");
							CollectionCaseDAO collectionCase=relToCase.getCollectionCaseDAO();
							updateAssignedAmount(collectionCase,preReferral.getAmount());
							session.saveOrUpdate(collectionCase);
						}
						else
						{
							logger.debug("preReferral.getAmount() is null so Collection Case assigned amount unchanged");
						}
					}
				}
				tx.commit();	
				logger.debug("Collection Case assigned amount changed successfully");
				result.put("RESULT", "SUCCESS");
			}catch (Exception e) {
				tx.rollback();
				result.put("ERROR", e.getMessage());
				e.printStackTrace();
			} finally {
				HibernateSessionFactory.closeSession(session);
			}
		}
		response.setData(result);
		return response; 
	}
	
	public DSResponse onDeletePreReferralAjustments(DSRequest dsRequest) throws Exception {
		logger.debug("called updateAssignedAmountPreReferralAjustments");
		Map values=dsRequest.getValues();
		Map<String,String> result=new LinkedHashMap<String,String>();
		DSResponse response=new DSResponse();
		if(values.get("id") != null)
		{
			Session session = StandaloneDAO.sessionOpen();
			Transaction tx=session.beginTransaction();
			try {
				CollectionCaseDAO collectionCaseDAO=(CollectionCaseDAO)session.get(CollectionCaseDAO.class, new Long(values.get("id").toString()));
				float assignedAmount =  FinancialFlow.initialPrincipalBalance(collectionCaseDAO);
				collectionCaseDAO.setAssignedAmount(String.valueOf(assignedAmount));
				session.saveOrUpdate(collectionCaseDAO);
				tx.commit();	
				logger.debug("Collection Case assigned amount changed successfully");
				result.put("RESULT", "SUCCESS");
			}catch (Exception e) {
				tx.rollback();
				result.put("ERROR", e.getMessage());
				e.printStackTrace();
			} finally {
				HibernateSessionFactory.closeSession(session);
			}
		}
		response.setData(result);
		return response; 
	}

	public DSResponse updateAssignedAmountPreReferralAjustments(DSRequest dsRequest) throws Exception { 
		
		logger.debug("called updateAssignedAmountPreReferralAjustments");
		Map values=dsRequest.getValues();
		Map<String,String> result=new LinkedHashMap<String,String>();
		DSResponse response=new DSResponse();
		if(values.get("id") != null)
		{
			Session session = StandaloneDAO.sessionOpen();
			Transaction tx=session.beginTransaction();
			try {
				PreReferralAdjustmentDAO preReferral=(PreReferralAdjustmentDAO)session.get(PreReferralAdjustmentDAO.class, new Long(values.get("id").toString()));
				Set<CaseToReferralAdjustmentDAO> preReferralObjects = (Set<CaseToReferralAdjustmentDAO>) preReferral.getCaseToReferralAdjustmentDAOs();
				if(preReferralObjects != null && preReferralObjects.size() > 0)
				{
					logger.debug("Found CaseToReferralPaymentDAO");
					
					for(CaseToReferralAdjustmentDAO relToCase:preReferralObjects)
					{
						if(preReferral.getAmount() != null)
						{
							logger.debug("Found preReferral.getAmount() : "+preReferral.getAmount());
							logger.debug("Updating collection case assigned amount");
							CollectionCaseDAO collectionCase=relToCase.getCollectionCaseDAO();
							updateAssignedAmount(collectionCase,preReferral.getAmount());
							session.saveOrUpdate(collectionCase);
						}
						else
						{
							logger.debug("preReferral.getAmount() is null so Collection Case assigned amount unchanged");
						}
					}
				}
				tx.commit();	
				logger.debug("Collection Case assigned amount changed successfully");
				result.put("RESULT", "SUCCESS");
			}catch (Exception e) {
				tx.rollback();
				result.put("ERROR", e.getMessage());
				e.printStackTrace();
			} finally {
				HibernateSessionFactory.closeSession(session);
			}
		}
		response.setData(result);
		return response; 
	}
	
	private void updateAssignedAmount(CollectionCaseDAO collectionCaseDAO, String amount) throws Exception
	{
		if(collectionCaseDAO != null)
		{
			String assignedAmount=null;
			if(collectionCaseDAO.getAssignedAmount() != null)
			{
				assignedAmount=collectionCaseDAO.getAssignedAmount();
			}
			else
			{
				assignedAmount=collectionCaseDAO.getOriginalAmount();
			}
			
			logger.debug("Assigned amount value : "+assignedAmount);
			
			if(assignedAmount == null)
			{
				logger.debug("Assigned Amount is null so Collection Case assigned amount setting failed");
				return;
			}
			
			if(amount == null)
			{
				logger.debug("Assigned Amount is null so Collection Case assigned amount setting failed");
			}
			
			if(assignedAmount != null && amount != null)
			{
				logger.debug("Deducting Amount : "+amount);
				float assignedAmountVal=(Conversion.floatValueOf(assignedAmount) - Conversion.floatValueOf(amount));
				collectionCaseDAO.setAssignedAmount(String.valueOf(assignedAmountVal));
				logger.debug("Updated the Assigned Amount : "+assignedAmountVal);
			}
			
		}
	}
	
	public DSResponse getDistinctParticipantName(DSRequest dsRequest) throws Exception {
		
		DSResponse dsResponse = new DSResponse();
		Map vals = dsRequest.getValues();
		Session session = StandaloneDAO.sessionOpen();

		Transaction tx = session.beginTransaction();
		
	    try {
	    		String hql="select distinct pv.partyName from CaseParticipantsCivil pv order by pv.partyName";
	    	
		      Query q = session.createQuery(hql);
		      List<String> records = q.list();
		      LinkedHashMap<String, String> map=new LinkedHashMap<String, String>();
		      if(records != null && (!records.isEmpty()))
		      {
		    	  for(String partyName : records)
		    	  {
		    		  map.put(partyName,partyName);
		    	  }
		    	 
		      }
	      
	      dsResponse.setData(map);	     	      						
		  tx.commit();

	    }
	    catch(Exception ex) {
	    	 tx.rollback();
	    	ex.printStackTrace();
	    }
	    finally {
	      if(tx.isActive()) {
	        tx.rollback();
	      }
	      session.close();
	    }
				
		return dsResponse;
	}
	private void outlookEntries(List<DefenderAssignmentDAO> defenderAssignment,List<DelinquencyStaffAssignmentDAO> delinquencyStaff, List<CaseStaffAssignmentDAO> caseStaff,List<String> entityType, Long currentPersonId, HttpSession httpSession) throws Exception {
		PersistenceSession persister = HibernateSessionFactory.getPersister();
		Session pSession=persister.getSession();		
		try{
			updateOutlookForDefenderAssignment(defenderAssignment,currentPersonId,persister,pSession,httpSession);//criminalCase
			updateOutlookForDelinquencyCaseStaff(delinquencyStaff, currentPersonId,persister,pSession,httpSession); //DelinquencyCase
			updateOutlookForCaseStaff(entityType,caseStaff,currentPersonId,persister,pSession,httpSession);//otherCase
		}
		 catch(Exception ex) {
		    	ex.printStackTrace();
		    }
		    finally {
		    	pSession.close();
		    }
	
	}

	private void updateOutlookForDefenderAssignment(List<DefenderAssignmentDAO> caseStaff, Long currentPersonId, PersistenceSession persister, Session pSession, HttpSession httpSession) throws Exception  {
		if(!caseStaff.isEmpty() &&caseStaff.size()>0){	
				for(int i=0;i<caseStaff.size();i++){
					
				  DefenderAssignmentDAO defenderAssignmentDAO=caseStaff.get(i);
				  if(defenderAssignmentDAO.getIsPrimary() != null && defenderAssignmentDAO.getIsPrimary().booleanValue()){
						com.legaledge.maestro.server.model.report.CriminalDefendantDAO criminalDefendantDAO=defenderAssignmentDAO.getCriminalDefendantDAO();
					Set<com.legaledge.maestro.server.model.report.DefendantToAppearanceDAO> defToAppearances=criminalDefendantDAO.getDefendantToAppearanceDAOs();
					if(defToAppearances != null && (!defToAppearances.isEmpty()))
					{
						com.legaledge.maestro.server.model.report.PersonDAO person=(com.legaledge.maestro.server.model.report.PersonDAO)pSession.get(com.legaledge.maestro.server.model.report.PersonDAO.class, defenderAssignmentDAO.getPersonId());
						com.legaledge.maestro.server.dao.generated.CurrentEntitiesDAO.exportOutlookEntris(persister,defToAppearances,person,defenderAssignmentDAO.getId(),"update",currentPersonId,httpSession);
					}	 
				  }
				}
		}	
	}
	private void updateOutlookForDelinquencyCaseStaff(List<DelinquencyStaffAssignmentDAO> caseStaff, Long currentPersonId, PersistenceSession persister, Session pSession,HttpSession httpSession) throws Exception {
		if(!caseStaff.isEmpty() && caseStaff.size()>0){
			for(int i=0;i<caseStaff.size();i++){				
				 DelinquencyStaffAssignmentDAO defenderAssignmentDAO=caseStaff.get(i);
				  if(defenderAssignmentDAO.getIsPrimary() != null && defenderAssignmentDAO.getIsPrimary().booleanValue() && CurrentEntitiesDAO.isAssignedAttorney("DelinquencyStaffAssignment",defenderAssignmentDAO.getRole())){
					com.legaledge.maestro.server.model.report.DelinquencyCaseDAO delinquencyCaseDAO=(com.legaledge.maestro.server.model.report.DelinquencyCaseDAO)pSession.get(com.legaledge.maestro.server.model.report.DelinquencyCaseDAO.class, defenderAssignmentDAO.getDelinquencyCaseId());
					com.legaledge.maestro.server.model.report.JuvenileOffenderDAO juvenileOffenderDAO=delinquencyCaseDAO.getJuvenileOffenderDAO();
					
					Set<com.legaledge.maestro.server.model.report.DefendantToAppearanceDAO> defToAppearances = juvenileOffenderDAO.getDefendantToAppearanceDAOs();
					if(defToAppearances != null && (!defToAppearances.isEmpty()))
					{
						com.legaledge.maestro.server.model.report.PersonDAO person=(com.legaledge.maestro.server.model.report.PersonDAO)pSession.get(com.legaledge.maestro.server.model.report.PersonDAO.class, defenderAssignmentDAO.getPersonId());
						CurrentEntitiesDAO.exportOutlookEntris(persister,defToAppearances,defenderAssignmentDAO.getPersonDAO(),defenderAssignmentDAO.getId(),"update",currentPersonId,httpSession);
					}	 
				  }
				}		
		}	
	}

	private void updateOutlookForCaseStaff(List<String> entityType, List<CaseStaffAssignmentDAO> caseStaff, Long currentPersonId, PersistenceSession persister, Session pSession, HttpSession httpSession) throws Exception {
		if(entityType.size()>0 && !caseStaff.isEmpty() && caseStaff.size()>0){
				for(int i=0;i<entityType.size();i++){
				   assignOtherCaseAttorneyOutlookEntries(caseStaff.get(i),entityType.get(i),currentPersonId,persister,pSession,httpSession);
				}
		}	
	}
	public void assignOtherCaseAttorneyOutlookEntries(com.legaledge.maestro.server.model.report.CaseStaffAssignmentDAO defenderAssignmentDAO,String entityType,Long currentPersonId, PersistenceSession persister, Session session, HttpSession httpSession) throws Exception
	{
		if(defenderAssignmentDAO!=null && entityType!=null && defenderAssignmentDAO.getIsPrimary() != null && defenderAssignmentDAO.getIsPrimary().booleanValue()
				&& com.legaledge.maestro.server.dao.generated.CurrentEntitiesDAO.isAssignedAttorney("CaseStaffAssignment",defenderAssignmentDAO.getRole()))
		{
			logger.debug("Entered into assignOtherCaseAttorneyOutlookEntries option");
			
					Set<com.legaledge.maestro.server.model.report.DefendantToAppearanceDAO> defToAppearances=null;
					
					if(entityType.equalsIgnoreCase("LitigationMatter") && defenderAssignmentDAO.getLitigationMatterId()!=null)
					{
						com.legaledge.maestro.server.model.report.LitigationMatterDAO caseLit=(com.legaledge.maestro.server.model.report.LitigationMatterDAO)session.get(com.legaledge.maestro.server.model.report.LitigationMatterDAO.class, defenderAssignmentDAO.getLitigationMatterId());
						defToAppearances =caseLit.getOpposingParty()!=null ? caseLit.getOpposingParty().getDefendantToAppearanceDAOs() : null;
					}
					else if(entityType.equalsIgnoreCase("DependencyCase") && defenderAssignmentDAO.getDependencyCaseId()!=null)
					{
						com.legaledge.maestro.server.model.report.DependencyCaseDAO caseObj=(com.legaledge.maestro.server.model.report.DependencyCaseDAO)session.get(com.legaledge.maestro.server.model.report.DependencyCaseDAO.class, defenderAssignmentDAO.getDependencyCaseId());
						defToAppearances = caseObj.getDependencyClientDAO()!=null ?caseObj.getDependencyClientDAO().getDefendantToAppearanceDAOs():null;
					}
					else if(entityType.equalsIgnoreCase("MentalHealthMatter") && defenderAssignmentDAO.getMentalHealthMatterId()!=null) 
					{
						com.legaledge.maestro.server.model.report.MentalHealthMatterDAO caseObj=(com.legaledge.maestro.server.model.report.MentalHealthMatterDAO)session.get(com.legaledge.maestro.server.model.report.MentalHealthMatterDAO.class, defenderAssignmentDAO.getMentalHealthMatterId());
						defToAppearances = caseObj.getMentalHealthPatient()!=null ?caseObj.getMentalHealthPatient().getDefendantToAppearanceDAOs():null;
					}
					else if(entityType.equalsIgnoreCase("TrafficCase") && defenderAssignmentDAO.getTrafficCaseId()!=null)
					{
						com.legaledge.maestro.server.model.report.TrafficCaseDAO caseObj=(com.legaledge.maestro.server.model.report.TrafficCaseDAO)session.get(com.legaledge.maestro.server.model.report.TrafficCaseDAO.class, defenderAssignmentDAO.getTrafficCaseId());
						defToAppearances = caseObj.getTrafficDefendantDAO()!=null ?caseObj.getTrafficDefendantDAO().getDefendantToAppearanceDAOs():null;
					}
					else if(entityType.equalsIgnoreCase("ContemptCase") && defenderAssignmentDAO.getContemptCaseId()!=null)
					{
						com.legaledge.maestro.server.model.report.ContemptCaseDAO caseObj=(com.legaledge.maestro.server.model.report.ContemptCaseDAO)session.get(com.legaledge.maestro.server.model.report.ContemptCaseDAO.class, defenderAssignmentDAO.getContemptCaseId());
						defToAppearances =  caseObj.getContemptDefendantDAO()!=null ?caseObj.getContemptDefendantDAO().getDefendantToAppearanceDAOs():null;
					}
					
					else if(entityType.equalsIgnoreCase("GeneralMatter") && defenderAssignmentDAO.getGeneralMatterId()!=null)
					{
						com.legaledge.maestro.server.model.report.GeneralMatterDAO caseObj=(com.legaledge.maestro.server.model.report.GeneralMatterDAO)session.get(com.legaledge.maestro.server.model.report.GeneralMatterDAO.class, defenderAssignmentDAO.getGeneralMatterId());
						defToAppearances = caseObj.getGeneralMatterMainPartyDAO()!=null ? caseObj.getGeneralMatterMainPartyDAO().getDefendantToAppearanceDAOs():null;
					}
					else if(entityType.equalsIgnoreCase("ContractMatter") && defenderAssignmentDAO.getContractMatterId()!=null)
					{
						com.legaledge.maestro.server.model.report.ContractMatterDAO caseObj=(com.legaledge.maestro.server.model.report.ContractMatterDAO)session.get(com.legaledge.maestro.server.model.report.ContractMatterDAO.class, defenderAssignmentDAO.getContractMatterId());
						defToAppearances = caseObj.getGeneralMatterMainPartyDAO()!=null ? caseObj.getGeneralMatterMainPartyDAO().getDefendantToAppearanceDAOs():null;
					}
					
					if(defToAppearances != null && (!defToAppearances.isEmpty()))
					{
						com.legaledge.maestro.server.model.report.PersonDAO person=(com.legaledge.maestro.server.model.report.PersonDAO)session.get(com.legaledge.maestro.server.model.report.PersonDAO.class, defenderAssignmentDAO.getPersonId());
						com.legaledge.maestro.server.dao.generated.CurrentEntitiesDAO.exportOutlookEntris(persister,defToAppearances,person,defenderAssignmentDAO.getId(),"update",currentPersonId,httpSession);
					}	  
		}
	}

	public DSResponse searchSpillmanOffenses(DSRequest req) throws Exception {
		SpillmanImportService spillmanImportService = new SpillmanImportService();
		Map values = req.getValues();
		String lawIncidentNumber = (String) values.get("DR Number");
		String courtDocketNumber = (String) values.get("CourtDocketNumber");
		String personName = (String) values.get("PersonName");

		DSResponse response = new DSResponse();
		spillmanIntegrationModel spm = spillmanImportService.personSearchDetails(personName, lawIncidentNumber, courtDocketNumber);
		if(spm.getSpillmanIntegrationModelList().size()>0)
			response.setData(spm.getSpillmanIntegrationModelList());

		logger.debug(LoginRestrictedPage.getMemoryUsage());
		return response;
	}
	public DSResponse createCriminalCases(DSRequest req) throws Exception {
		try {
			Map values = req.getValues();
			ArrayList offenses = (ArrayList) values.get("Offenses");
			spillmanIntegrationModel spillmanOffensesList = new spillmanIntegrationModel();
			ArrayList<spillmanIntegrationModel> selectedOffensesList = new ArrayList<spillmanIntegrationModel>();
			LinkedMap rowMap = null;
			int offensesCount = offenses.size();
			for(int i=0; i < offensesCount ; i++){
				System.out.println("case ids Selected"+((LinkedMap)offenses.get(i)).get("caseId"));
				rowMap = (LinkedMap)offenses.get(i);
				spillmanIntegrationModel spillmanOffensesrow = new spillmanIntegrationModel();
				if(rowMap.containsKey("caseId") && rowMap.get("caseId") != null){
					spillmanOffensesrow.setCaseId(rowMap.get("caseId").toString());
				}
				if(rowMap.containsKey("personId") && rowMap.get("personId") != null) {
					spillmanOffensesrow.setPersonId(rowMap.get("personId").toString());
				}
				if(rowMap.containsKey("personName") && rowMap.get("personName") != null) {
					spillmanOffensesrow.setPersonName(rowMap.get("personName").toString());
				}
				if(rowMap.containsKey("incidentNumber") && rowMap.get("incidentNumber") != null) {
					spillmanOffensesrow.setIncidentNumber(rowMap.get("incidentNumber").toString());
				}
				if(rowMap.containsKey("offenseCode") && rowMap.get("offenseCode") != null) {
					spillmanOffensesrow.setOffenseCode(rowMap.get("offenseCode").toString());
				}
				if(rowMap.containsKey("statue") && rowMap.get("statue") != null) {
					spillmanOffensesrow.setStatue(rowMap.get("statue").toString());
				}
				if(rowMap.containsKey("crimeClass") && rowMap.get("crimeClass") != null) {
					spillmanOffensesrow.setCrimeClass(rowMap.get("crimeClass").toString());
				}
				if(rowMap.containsKey("location") && rowMap.get("location") != null) {
					spillmanOffensesrow.setLocation(rowMap.get("location").toString());
				}
				if(rowMap.containsKey("courtArea") && rowMap.get("courtArea") != null) {
					spillmanOffensesrow.setCourtArea(rowMap.get("courtArea").toString());
				}
				if(rowMap.containsKey("courtDocketNumber") && rowMap.get("courtDocketNumber") != null) {
					spillmanOffensesrow.setCourtDocketNumber(rowMap.get("courtDocketNumber").toString());
				}
				if(rowMap.containsKey("nameNumber") && rowMap.get("nameNumber") != null) {
					spillmanOffensesrow.setNameNumber(rowMap.get("nameNumber").toString());
				}
				
				selectedOffensesList.add(spillmanOffensesrow);
				
				if(i == (offensesCount-1)){
					spillmanOffensesList.setSpillmanIntegrationModelList(selectedOffensesList);
				}
			}
			
			DSResponse response = new DSResponse();
			
			SpillmanImportService spillmanservices = new SpillmanImportService();
			String res =  spillmanservices.getImportDetailsForSelectedSpillmanOffenses(spillmanOffensesList,false);
			
			//response.setData(res);
			/*DSResponse response = new DSResponse();
			
			SpillmanNavajoJob.runimportBatch();*/
			
			response.setStatus(DSResponse.STATUS_SUCCESS);
			
			logger.debug(LoginRestrictedPage.getMemoryUsage());
			return response;
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	}
	public DSResponse importPendingCriminalCases(DSRequest req) throws Exception {
		
	DSResponse response = new DSResponse();
	List<PendingImportPersonList> criminalCasesPendingImportsList = (ArrayList<PendingImportPersonList>) InsertNCAOData.getPendingCaseDetailsList();
	if(criminalCasesPendingImportsList.size()>0)
		response.setData(criminalCasesPendingImportsList);

	logger.debug(LoginRestrictedPage.getMemoryUsage());
	return response;
}
	public DSResponse checkDuplicateDependencyCase(DSRequest req) throws Exception {


		try {
			
			Map values = req.getValues();
			String importId = (String) values.get("importId");
           
			
			Persister persister = (Persister) PersisterUtils.getPersister();

            String caseId = null;   
            if(importId!=null)
            {
            	caseId =CheckDuplicate("SELECT TOP 1 id FROM hwe.case_file  WITH(NOLOCK) where import_id  like '" + importId+"%'" ,persister);
            }
            
			DSResponse response = new DSResponse();
			Map<String,String> result=new LinkedHashMap<String, String>();
            if(caseId != null && (!caseId.equals("0")))
            {
    			result.put("result", "Existing Case");
    			result.put("caseId", caseId);
            }
            else{
    			result.put("result", "New Case");
    			result.put("caseId", caseId);
            }
			response.setData(result);
            persister.close();
            return response;
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	
	
	}
	public DSResponse checkDependencyCaseForCourt(DSRequest req) throws Exception {


		try {
			
			Map values = req.getValues();
			String importId = (String) values.get("importId");
			String fileNumber = (String) values.get("sacwisId");
			String courtNumber = (String) values.get("courtNumber");
           
			
			Persister persister = (Persister) PersisterUtils.getPersister();

            String caseId = "0";   
            String resultImportId = "0";   
            if(importId!=null && fileNumber!=null && courtNumber!=null)
            {
            	String sqlQuery="select  top 1 c.id,c.import_id from hwe.case_file c join report.case_court_ cc on cc.t$dependency_case_=c.id where cc.court_number_='"+courtNumber+"' and c.file_number='"+fileNumber+"' and c.import_id  like '" + importId+"%'";            
                 Object[] obj = persister.runSQLQuery(sqlQuery);
                 ResultSet cursor = (ResultSet) obj[1];
                 try {
                     while (cursor.next()) {
                    	 caseId = cursor.getString(1);
                    	 resultImportId = cursor.getString(2);
                	 
                     }
                 } catch (SQLException e) {
                     logger.error(e);
                   
                 }
            }
            
			DSResponse response = new DSResponse();
			Map<String,String> result=new LinkedHashMap<String, String>();
            if(caseId != null && (!caseId.equals("0")) && resultImportId != null && (!resultImportId.equals("0")))
            {
    			result.put("result", "Existing Case");
    			result.put("caseId", caseId);
    			result.put("importId", resultImportId);
            }
            else{
    			result.put("result", "New Case");
            }
			response.setData(result);
            persister.close();
            return response;
		} catch (Exception e) {
			e.printStackTrace();
			throw e;
		}
	
	
	}
	public static String CheckDuplicate(String query, PersistenceSession p) {
        String id = "0";
        Object[] obj = p.runSQLQuery(query);

        ResultSet cursor = (ResultSet) obj[1];
        try {
            while (cursor.next()) {
                id = cursor.getString(1);
            }
        } catch (SQLException e) {
            logger.error(e);
            id = "0";
        }
        return id;
    }
	public static String getNextImportId(String importId){
		Persister p=null;
		try {
			p = (Persister) PersisterUtils.getPersister();
			String query="SELECT count(*)  FROM hwe.case_file  WITH(NOLOCK) where import_id  like '" + importId+"%'" ;
			Object[] obj = p.runSQLQuery(query);
			 ResultSet cursor = (ResultSet) obj[1];
		        try {
		            while (cursor.next()) {
		            	int count= cursor.getInt(1);
		            	if(count>0){
		            		importId=importId+"_"+(count+1);
		            	}
		            	
		            }
		        } catch (SQLException e) {
		            logger.error(e);
		            
		        }	
		} catch (Exception e) {
			e.printStackTrace();
		}
		finally{
			 p.close();
		}
		return importId;
		
	}
	public DSResponse createDependencyCase(DSRequest req) throws Exception {

		Map values = req.getValues();
		String sacwisId = values.get("SacwisId").toString();	
		String familyNumber = values.get("familyNumber").toString();		
		ArrayList selectedParticipants = (ArrayList) values.get("SelectedParticipants");
		
		String importId = values.get("importId").toString();		
		String courtNumber="null";
		if(values.get("courtNumber")!=null){
			importId=getNextImportId(importId);
			courtNumber=values.get("courtNumber").toString();
		}
		
		System.out.println("importId "+importId+"sacwisId "+sacwisId+"familyNumber "+familyNumber);

		LinkedMap rowMap = null;
		StringBuffer attorneys=new StringBuffer();	
		StringBuffer workers=new StringBuffer();
		StringBuffer child=new StringBuffer();	
		StringBuffer otherPerson=new StringBuffer();	//other case participant from client table(person)
		StringBuffer otherOrganization=new StringBuffer();	//other case participant from client table(person)
		StringBuffer otherCasePerson=new StringBuffer();	//other case participant from court hearing,court master,professional,parties(person)


		if(selectedParticipants!=null && selectedParticipants.size()>0){
			for(int i=0;i<selectedParticipants.size();i++){
				rowMap = (LinkedMap)selectedParticipants.get(i);
				
				if(rowMap.containsKey("role") && rowMap.get("role") != null  && rowMap.containsKey("personId") && rowMap.get("personId") != null ){
					String role=rowMap.get("role").toString();
					String primaryId=rowMap.get("personId").toString();
					String tableName=rowMap.get("tableName").toString();
				if(role.equals("Assigned Attorney")){
					if(attorneys.length() > 0)
						attorneys.append(",");				
					attorneys.append(primaryId);//court nbr is a person id
				}
				else if(role.equals("Worker")){
					if(workers.length() > 0)
						workers.append(",");				
					workers.append(primaryId);			
				}
				else if(role.equals("Child") ){
					if(child.length() > 0)
						child.append(",");				
					child.append(primaryId);				
				}
				else if(tableName.equals("Client") && !role.equals("Child") ){
					if(otherPerson.length() > 0)
						otherPerson.append(",");				
					otherPerson.append(primaryId);				
				}
				else if(tableName.equals("Company")  ){
					if(otherOrganization.length() > 0)
						otherOrganization.append(",");				
					otherOrganization.append(primaryId);				
				}
				else{
					if(otherCasePerson.length() > 0)
						otherCasePerson.append(",");				
					otherCasePerson.append(primaryId);				
				
				}
			}
		 }
		}
		
		//get Transaction id
		String transactionId=null;
		if(importId!=null && importId.length()>0){
			transactionId=importId.replace("_", "").substring(2);
		}
		DSResponse response = new DSResponse();
		
		Connection connection=getCon();
		CallableStatement csInsert = connection.prepareCall("exec dbo.sacwis_dependency_case_records ?,?,?,?");	
		csInsert.setString(1, sacwisId.toString());
		csInsert.setString(2, importId.toString());
		csInsert.setString(3, transactionId.toString());
		csInsert.setString(4, courtNumber);
		csInsert.execute();
		csInsert.close();
		
		if(attorneys!=null && attorneys.length() > 0){
			CallableStatement attorneyInsert = connection.prepareCall("exec dbo.sacwis_case_staff_assignment_attorney ?,?,?,?,?");	
			attorneyInsert.setString(1, sacwisId.toString());
			attorneyInsert.setString(2, attorneys.toString());
			attorneyInsert.setString(3, familyNumber);
			attorneyInsert.setString(4, importId.toString());
			attorneyInsert.setString(5, transactionId.toString());
			attorneyInsert.execute();
			attorneyInsert.close();
		}
		if(workers!=null && workers.length() > 0){
			CallableStatement workerInsert = connection.prepareCall("exec dbo.sacwis_case_staff_assignment_worker ?,?,?,?,?");	
			workerInsert.setString(1, sacwisId.toString());
			workerInsert.setString(2, workers.toString());
			workerInsert.setString(3, familyNumber);
			workerInsert.setString(4, importId.toString());
			workerInsert.setString(5, transactionId.toString());
			workerInsert.execute();
			workerInsert.close();
		}
		if(child!=null && child.length() > 0){
			CallableStatement workerInsert = connection.prepareCall("exec dbo.sacwis_dependency_client_records ?,?,?,?,?");	
			workerInsert.setString(1, sacwisId.toString());
			workerInsert.setString(2, child.toString());
			workerInsert.setString(3, familyNumber);
			workerInsert.setString(4, importId.toString());
			workerInsert.setString(5, transactionId.toString());
			workerInsert.execute();
			workerInsert.close();
		}
	   if(otherPerson!=null && otherPerson.length() > 0){
			CallableStatement workerInsert = connection.prepareCall("exec dbo.sacwis_other_case_participant_client_records ?,?,?,?,?");	
			workerInsert.setString(1, sacwisId.toString());
			workerInsert.setString(2, otherPerson.toString());
			workerInsert.setString(3, familyNumber);
			workerInsert.setString(4, importId.toString());
			workerInsert.setString(5, transactionId.toString());
			workerInsert.execute();
			workerInsert.close();
		}
	   if(otherOrganization!=null && otherOrganization.length() > 0){
			CallableStatement workerInsert = connection.prepareCall("exec dbo.sacwis_other_case_participants_organization ?,?,?,?,?");	
			workerInsert.setString(1, sacwisId.toString());
			workerInsert.setString(2, otherOrganization.toString());
			workerInsert.setString(3, familyNumber);
			workerInsert.setString(4, importId.toString());
			workerInsert.setString(5, transactionId.toString());
			workerInsert.execute();
			workerInsert.close();
		}
	   if(otherCasePerson!=null && otherCasePerson.length() > 0){
			CallableStatement workerInsert = connection.prepareCall("exec dbo.sacwis_other_case_participant_records ?,?,?,?,?");	
			workerInsert.setString(1, sacwisId.toString());
			workerInsert.setString(2, otherCasePerson.toString());
			workerInsert.setString(3, familyNumber);
			workerInsert.setString(4, importId.toString());
			workerInsert.setString(5, transactionId.toString());
			workerInsert.execute();
			workerInsert.close();
		}
		
		
		
		connection.close();
		Thread.sleep(3000);
		
		return response;
	
	}
	public DSResponse importPendingDependencyCases(DSRequest req) throws Exception {
		
		DSResponse response = new DSResponse();
		System.out.println("Calling to fetch pending import list for SACWIS");
		List<SACWISPendingImportPersonList> dependencyCasesPendingImportsList = (ArrayList<SACWISPendingImportPersonList>) InsertNCAOData.getSACWISPendingCaseDetailsList();
		if(dependencyCasesPendingImportsList.size()>0)
			response.setData(dependencyCasesPendingImportsList);

		logger.debug(LoginRestrictedPage.getMemoryUsage());
		return response;
	}
	public DSResponse reOpenCase(DSRequest dsRequest) throws Exception { 
		Map values = dsRequest.getValues();
		
		if(values.get("caseId") == null)
		{
			throw new Exception("Select valid record");
		}
		
		DSResponse dsResponse =new DSResponse(); 
		if(values.get("caseId") != null)
		{
			String returnValue="Could not find selected case";
			Session session = StandaloneDAO.sessionOpen();
			Transaction tx = session.beginTransaction();
			try {
				if(values.get("caseType")!=null && values.get("caseType").toString().equals("Litigation")){
					com.legaledge.maestro.server.model.report.LitigationMatterDAO caseList=(com.legaledge.maestro.server.model.report.LitigationMatterDAO)session.get(com.legaledge.maestro.server.model.report.LitigationMatterDAO.class, new Long(values.get("caseId").toString()));
					if(caseList!=null){
						caseList.setIsOpen(Boolean.TRUE);
						caseList.setRecordClassification(null);
						caseList.setActualDestructionDate(null);
						caseList.setEligibleDestructionDate(null);
						caseList.setDestructionType(null);
						caseList.setPersonId(null);
						caseList.setDateClosed(null);
						PicklistValue status=MetadataCache.picklistValueFor("CaseStatus","Open");
						if(status!=null){
							caseList.setCaseStatus(status.getId());
						}
						session.saveOrUpdate(caseList);
					}
				}
				else if(values.get("caseType")!=null && values.get("caseType").toString().equals("Claim")){

					com.legaledge.maestro.server.model.report.ClaimDAO caseList=(com.legaledge.maestro.server.model.report.ClaimDAO)session.get(com.legaledge.maestro.server.model.report.ClaimDAO.class, new Long(values.get("caseId").toString()));
					if(caseList!=null){
						caseList.setIsOpen(Boolean.TRUE);
						caseList.setRecordClassification(null);
						caseList.setActualDestructionDate(null);
						caseList.setEligibleDestructionDate(null);
						caseList.setDestructionType(null);
						caseList.setPersonId(null);
						caseList.setDateClosed(null);
						PicklistValue status=MetadataCache.picklistValueFor("ClaimStatus","Open");
						if(status!=null){
							caseList.setCaseStatus(status.getId());
						}
						session.saveOrUpdate(caseList);
					}
				
				}
				else if(values.get("caseType")!=null && values.get("caseType").toString().equals("Collection")){
					com.legaledge.maestro.server.model.report.CollectionCaseDAO caseList=(com.legaledge.maestro.server.model.report.CollectionCaseDAO)session.get(com.legaledge.maestro.server.model.report.CollectionCaseDAO.class, new Long(values.get("caseId").toString()));
					if(caseList!=null){
						caseList.setIsOpen(Boolean.TRUE);
						caseList.setRecordClassification(null);
						caseList.setActualDestructionDate(null);
						caseList.setEligibleDestructionDate(null);
						caseList.setDestructionType(null);
						caseList.setPersonId(null);
						caseList.setEndDate(null);
						PicklistValue status=MetadataCache.picklistValueFor("CollectionCaseStatus","Open");
						if(status!=null){
							caseList.setStatusPv(status.getId());
						}
						session.saveOrUpdate(caseList);
					}
				
				}
				else if(values.get("caseType")!=null && values.get("caseType").toString().equals("Criminal")){

					com.legaledge.maestro.server.model.report.CriminalCaseDAO caseList=(com.legaledge.maestro.server.model.report.CriminalCaseDAO)session.get(com.legaledge.maestro.server.model.report.CriminalCaseDAO.class, new Long(values.get("caseId").toString()));
					if(caseList!=null){
						caseList.setIsOpen(Boolean.TRUE);
						caseList.setRecordClassification(null);
						caseList.setActualDestructionDate(null);
						caseList.setEligibleDestructionDate(null);
						caseList.setDestructionType(null);
						caseList.setPersonId(null);
						caseList.setEndDate(null);
						PicklistValue status=MetadataCache.picklistValueFor("CriminalStatus","OPEN");
						if(status!=null){
							caseList.setCriminalStatus(status.getId());
						}
						session.saveOrUpdate(caseList);
					}
				
				}
				else if(values.get("caseType")!=null && values.get("caseType").toString().equals("General")){

					com.legaledge.maestro.server.model.report.GeneralMatterDAO caseList=(com.legaledge.maestro.server.model.report.GeneralMatterDAO)session.get(com.legaledge.maestro.server.model.report.GeneralMatterDAO.class, new Long(values.get("caseId").toString()));
					if(caseList!=null){
						caseList.setIsOpen(Boolean.TRUE);
						caseList.setRecordClassification(null);
						caseList.setActualDestructionDate(null);
						caseList.setEligibleDestructionDate(null);
						caseList.setDestructionType(null);
						caseList.setPersonId(null);
						caseList.setDateClosed(null);
						PicklistValue status=MetadataCache.picklistValueFor("CaseStatus","Open");
						if(status!=null){
							caseList.setCaseStatus(status.getId());
						}
						session.saveOrUpdate(caseList);
					}
				
				}
				else if(values.get("caseType")!=null && values.get("caseType").toString().equals("Contract")){

					com.legaledge.maestro.server.model.report.ContractMatterDAO caseList=(com.legaledge.maestro.server.model.report.ContractMatterDAO)session.get(com.legaledge.maestro.server.model.report.ContractMatterDAO.class, new Long(values.get("caseId").toString()));
					if(caseList!=null){
						caseList.setIsOpen(Boolean.TRUE);
						caseList.setRecordClassification(null);
						caseList.setActualDestructionDate(null);
						caseList.setEligibleDestructionDate(null);
						caseList.setDestructionType(null);
						caseList.setPersonId(null);
						caseList.setDateClosed(null);
						PicklistValue status=MetadataCache.picklistValueFor("ContractMatterStatus","Open");
						if(status!=null){
							caseList.setStatusPv(status.getId());
						}
						session.saveOrUpdate(caseList);
					}
				
				}
				else if(values.get("caseType")!=null && values.get("caseType").toString().equals("Delinquency")){

					com.legaledge.maestro.server.model.report.DelinquencyCaseDAO caseList=(com.legaledge.maestro.server.model.report.DelinquencyCaseDAO)session.get(com.legaledge.maestro.server.model.report.DelinquencyCaseDAO.class, new Long(values.get("caseId").toString()));
					if(caseList!=null){
						caseList.setIsOpen(Boolean.TRUE);
						caseList.setRecordClassification(null);
						caseList.setActualDestructionDate(null);
						caseList.setEligibleDestructionDate(null);
						caseList.setDestructionType(null);
						caseList.setPersonId(null);
						caseList.setEndDate(null);
						PicklistValue status=MetadataCache.picklistValueFor("DelinquencyPetitionStatus","OPEN");
						if(status!=null){
							caseList.setPetitionStatus(status.getId());
						}
						session.saveOrUpdate(caseList);
					}
				
				}
				else if(values.get("caseType")!=null && values.get("caseType").toString().equals("Dependency")){

					com.legaledge.maestro.server.model.report.DependencyCaseDAO caseList=(com.legaledge.maestro.server.model.report.DependencyCaseDAO)session.get(com.legaledge.maestro.server.model.report.DependencyCaseDAO.class, new Long(values.get("caseId").toString()));
					if(caseList!=null){
						caseList.setIsOpen(Boolean.TRUE);
						caseList.setRecordClassification(null);
						caseList.setActualDestructionDate(null);
						caseList.setEligibleDestructionDate(null);
						caseList.setDestructionType(null);
						caseList.setPersonId(null);
						caseList.setEndDate(null);
						PicklistValue status=MetadataCache.picklistValueFor("DependencyStatus","OPEN");
						if(status!=null){
							caseList.setDependencyStatus(status.getId());
						}
						session.saveOrUpdate(caseList);
					}
				
				}
				else if(values.get("caseType")!=null && values.get("caseType").toString().equals("MentalHealth")){

					com.legaledge.maestro.server.model.report.MentalHealthMatterDAO caseList=(com.legaledge.maestro.server.model.report.MentalHealthMatterDAO)session.get(com.legaledge.maestro.server.model.report.MentalHealthMatterDAO.class, new Long(values.get("caseId").toString()));
					if(caseList!=null){
						caseList.setIsOpen(Boolean.TRUE);
						caseList.setRecordClassification(null);
						caseList.setActualDestructionDate(null);
						caseList.setEligibleDestructionDate(null);
						caseList.setDestructionType(null);
						caseList.setPersonId(null);
						caseList.setEndDate(null);
						PicklistValue status=MetadataCache.picklistValueFor("MentalHealthStatus","OPEN");
						if(status!=null){
							caseList.setMentalHealthStatus(status.getId());
						}
						session.saveOrUpdate(caseList);
					}
				
				}
				else if(values.get("caseType")!=null && values.get("caseType").toString().equals("Contempt")){

					com.legaledge.maestro.server.model.report.ContemptCaseDAO caseList=(com.legaledge.maestro.server.model.report.ContemptCaseDAO)session.get(com.legaledge.maestro.server.model.report.ContemptCaseDAO.class, new Long(values.get("caseId").toString()));
					if(caseList!=null){
						caseList.setIsOpen(Boolean.TRUE);
						caseList.setRecordClassification(null);
						caseList.setActualDestructionDate(null);
						caseList.setEligibleDestructionDate(null);
						caseList.setDestructionType(null);
						caseList.setPersonId(null);
						caseList.setEndDate(null);
						PicklistValue status=MetadataCache.picklistValueFor("ContemptStatus","OPEN");
						if(status!=null){
							caseList.setContemptStatus(status.getId());
						}
						session.saveOrUpdate(caseList);
					}
				
				}
				else if(values.get("caseType")!=null && values.get("caseType").toString().equals("Forfeiture")){

					com.legaledge.maestro.server.model.report.ForfeitureCaseDAO caseList=(com.legaledge.maestro.server.model.report.ForfeitureCaseDAO)session.get(com.legaledge.maestro.server.model.report.ForfeitureCaseDAO.class, new Long(values.get("caseId").toString()));
					if(caseList!=null){
						caseList.setIsOpen(Boolean.TRUE);
						caseList.setRecordClassification(null);
						caseList.setActualDestructionDate(null);
						caseList.setEligibleDestructionDate(null);
						caseList.setDestructionType(null);
						caseList.setPersonId(null);
						caseList.setEndDate(null);
						PicklistValue status=MetadataCache.picklistValueFor("ForfeitureStatus","Active");
						if(status!=null){
							caseList.setForfeitureStatus(status.getId());
						}
						session.saveOrUpdate(caseList);
					}
				
				}
				
				tx.commit();
				Persister p = (Persister) PersisterUtils.getPersister();
				PersisterUtils.cascadeDelete(p, new Long(values.get("id").toString()));
				returnValue="ReOpened Sucessfully";
			}catch (Exception e) {
				returnValue = "Error in ReOpening the case.";
				e.printStackTrace();
				tx.rollback();
			} finally {
				HibernateSessionFactory.closeSession(session);
			}
			
			dsResponse.setData(returnValue);
		}
		return dsResponse; 
	}
	public void downloadAdhocCSV(DSRequest dsRequest, RPCManager rpc)  
    {  
	logger.debug("downloadCSV is called");
    DSResponse dsResponse = new DSResponse();  
    try {  
        rpc.doCustomResponse();  

        dsResponse = dsRequest.execute();  
         
        Map values=dsRequest.getValues();
       
        HttpServletResponse response = rpc.getContext().response;  
        response.setHeader("content-disposition", "attachment; filename="+values.get("fileName")+".csv");  
        //response.setContentType("text/plain");  
        response.setContentType("application/vnd.ms-excel");
      /*  response.setHeader("content-disposition", "inline; filename="+values.get("fileName")+".pdf");
        response.setContentType("application/pdf");  
       */
        logger.debug("downloadCSV bilding header");
       
        List<String[]> csvValueList=new ArrayList<String[]>();
        
        List<Map> selectedFieldRecords=(List<Map>)values.get("selectedFieds");
        List<String> selectedFiels=new ArrayList<String>();
        for(Map record :selectedFieldRecords){
        	selectedFiels.add(record.get("stringValue").toString());
        }
        
        //general Form
        Map generalForm=(Map) values.get("generalForm");
        if(!generalForm.isEmpty()&& generalForm.size()>0){
        	if(generalForm.get("description")!=null && (Boolean)generalForm.get("description")){
        		selectedFiels.add("Description");
        	}
        	if(generalForm.get("statusDescription")!=null && (Boolean)generalForm.get("statusDescription")){
        		selectedFiels.add("Status Desc");
        	}
        	if(generalForm.get("dispositionDescription")!=null && (Boolean)generalForm.get("dispositionDescription")){
        		selectedFiels.add("Disposition Desc");
        	}
        }
       
			addContents(selectedFiels, csvValueList, null);// header

			logger.debug("downloadCSV header done");

			List<Map> records=(List<Map>)values.get("records"); // case Records
     
        StringBuffer ids=new StringBuffer();
        Session session=StandaloneDAO.sessionOpen();
        for(Map record :records){
        	ids.append(record.get("id").toString());
        	ids.append(',');
        }
        String idResult=ids.substring(0,ids.length()-1);
        System.out.println("IDS "+idResult);
        
        
    	String hql="from LitClaimReports reports where reports.id in ("+idResult+")";
    	Query query=session.createQuery(hql);
		List<LitClaimReports> litClaim=query.list();
		for(LitClaimReports li : litClaim){
			addContents(selectedFiels,csvValueList,li);
		}

           
            session.close();
           
            logger.debug("downloadCSV Writing to csv");
           
            StringWriter strWriter = new StringWriter();
           
            CSVWriter csvWriter=new CSVWriter(strWriter);
            csvWriter.writeAll(csvValueList);
            csvWriter.close();
           
            logger.debug("downloadCSV writing done");
           
        ServletOutputStream out = response.getOutputStream();  
        out.write(strWriter.toString().getBytes());  
        dsResponse.setStatus(DSResponse.STATUS_SUCCESS);  
       
        rpc.send(dsRequest, dsResponse);  
       
        logger.debug("Succcess");
       
    } catch (Exception e) {
            dsResponse.setStatus(DSResponse.STATUS_FAILURE);
            e.printStackTrace();
            logger.debug("Failed download csv");
            try {  
            rpc.sendFailure(dsRequest, e.getMessage());  
        } catch(Exception er) {}  
    }  
    }

	private void addContents(List<String> selectedFiels, List<String[]> csvValueList, LitClaimReports reports) {
		List<String>  contents=new ArrayList<>();
		SimpleDateFormat sf=new SimpleDateFormat("MM/dd/yyyy");

		if (reports == null) { //heading
			if (selectedFiels.contains("File #")) {
				contents.add("File #");
			}
			if (selectedFiels.contains("File Name")) {
				contents.add("File Name");
			}
			if (selectedFiels.contains("Filed Date")) {
				contents.add("Date Filed");
			}
			if (selectedFiels.contains("Date Served")) {
				contents.add("Date Served");
			}	
			if (selectedFiels.contains("Court")) {
				contents.add("Court");
			}
			if (selectedFiels.contains("Court Number")) {
				contents.add("Court Number");
			}	
			if (selectedFiels.contains("Cause Desc")) {
				contents.add("Cause Desc");
			}
			if (selectedFiels.contains("Dispo")) {
				contents.add("Dispo");
			}
			if (selectedFiels.contains("Dispos Date")) {
				contents.add("Dispos Date");
			}	
			if (selectedFiels.contains("Disposition Desc")) {
				contents.add("Disposition Desc");
			}	
			if (selectedFiels.contains("Status")) {
				contents.add("Status");
			}	
			if (selectedFiels.contains("Status Desc")) {
				contents.add("Status Desc");
			}	
		
			if (selectedFiels.contains("Description")) {
				contents.add("Description");
			}	
			
			
			
			
		}
		else{
			if (selectedFiels.contains("File #")) {
				contents.add(reports.getFileNumber());
			}
			if (selectedFiels.contains("File Name")) {
				contents.add(reports.getCaseName());
			}
			if (selectedFiels.contains("Filed Date")) {
				contents.add(reports.getDateFiled()!=null ?sf.format(reports.getDateFiled()):"");
			}
			if (selectedFiels.contains("Date Served")) {
				contents.add(reports.getDateServed()!=null ?sf.format(reports.getDateServed()):"");
			}	
			if (selectedFiels.contains("Court")) {
				contents.add(reports.getCourtName());
			}
			if (selectedFiels.contains("Court Number")) {
				contents.add(reports.getCourtNumber());
			}	
			if (selectedFiels.contains("Cause Desc")) {
				contents.add(reports.getCauseDescription());
			}
			if (selectedFiels.contains("Dispo")) {
				contents.add(reports.getDisposition());
			}
			if (selectedFiels.contains("Dispos Date")) {
				contents.add(reports.getDispositionDate()!=null ?sf.format(reports.getDispositionDate()):"");
			}	
			if (selectedFiels.contains("Disposition Desc")) {
				contents.add(reports.getDispositionDescription());
			}	
			if (selectedFiels.contains("Status")) {
				contents.add(reports.getStatus());
			}	
			if (selectedFiels.contains("Status Desc")) {
				contents.add(reports.getStatusDescription());
			}	
			if (selectedFiels.contains("Description")) {
				contents.add(reports.getDescription());
			}	
		}
		
		String[] stringArray = contents.toArray(new String[0]);
		csvValueList.add(stringArray);
	}
	public void causeAndSummaryReports(DSRequest dsRequest, RPCManager rpc)  {
		  
				logger.debug("print Case and Summary report is called");
		        DSResponse dsResponse = new DSResponse();  
		        Session session = StandaloneDAO.sessionOpen();
		      //  Transaction tx=session.beginTransaction();
		        try 
		        {  
		            rpc.doCustomResponse();  
		  
		            dsResponse = dsRequest.execute();  
		            
		            Map values=dsRequest.getValues();
		           
		            HttpServletResponse response = rpc.getContext().response;  
		            response.setHeader("content-disposition", "inline; filename="+values.get("fileName")+".pdf");
		            response.setContentType("application/pdf");  
		            
		            List litigationRecords=(List) values.get("litigationRecords");
		            List  claimRecords=(List) values.get("claimRecords");
		            System.out.println(litigationRecords+"    "+claimRecords);
		          
		          /*  //form details
		            Map generalForm=(Map) values.get("generalForm");  //general Form
		            Map statusForm=(Map) values.get("statusForm");  //Status
		            Map litigationForm=(Map) values.get("litigationForm"); //Litigation
		            
		            List dateRange=(List) litigationForm.get("DateRange");
		            Map dateServed=(Map) litigationForm.get("dateServed");
		            Map dateFiled=(Map) litigationForm.get("dateFiled");

		            Map claimForm=(Map) values.get("claimForm");
		            Map datesForm=(Map) values.get("datesForm");	  */
		           
		          /*  String litigationQuery= getClaimLitigationClaimRecords("LitigationMatter",generalForm,statusForm,litigationForm,datesForm);
		        	String claimQuery= getClaimLitigationClaimRecords("Claim",generalForm,statusForm,claimForm,datesForm);

		        	Query lq = session.createQuery(litigationQuery);
					List<LitClaimReports> litRecords = lq.list();
		        	if(litRecords!=null && !litRecords.isEmpty() && litRecords.size()>0){
		        		System.out.println("list of litigation Records "+litRecords.size());
		        	}
		        	
		        	Query cq = session.createQuery(claimQuery);
					List<LitClaimReports> claimRecords = cq.list();
		        	if(claimRecords!=null && !claimRecords.isEmpty() && claimRecords.size()>0){
		        		System.out.println("list of Claim Records "+claimRecords.size());
		        	}*/
		            
		            // write a code to fetch records from LitClaimReports table based on above criteria
		            
		          /*   if(records != null && records.size() > 0)
		            {
		            	PDDocument printpdf=new PDDocument();
		              
		            	for(Map map:records)
		            	{
				            if(map.get("id") != null)
				            {
				            	//needs to print the report here
				            }
		            	}

		            	ServletOutputStream out = response.getOutputStream();  		
			            try(ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			            	printpdf.save(output);
			                byte[] bytes = output.toByteArray();
			                out.write(bytes);
			                response.setContentLength((int) bytes.length);
			            }
			            printpdf.close();
			            dsResponse.setStatus(DSResponse.STATUS_SUCCESS);  
			          //  tx.commit();
			            rpc.send(dsRequest, dsResponse);  
			            
			            logger.debug("Succcess");
		            }
			        else
			        {
			            dsResponse.setStatus(DSResponse.STATUS_FAILURE);
			            rpc.sendFailure(dsRequest, "No Cause and Summary report Found");  
			        }*/
		            
		        } catch (Exception e) {
		        //	tx.rollback();
		        	dsResponse.setStatus(DSResponse.STATUS_FAILURE);
		        	e.printStackTrace();
		        	logger.debug("Failed  Printing Cause and Summary report");
		        	try {  
		                rpc.sendFailure(dsRequest, e.getMessage());  
		            } catch(Exception er) {
		            	e.printStackTrace();
		            }  
		        }  
		        finally {
		        	HibernateSessionFactory.closeSession(session);
		        }
		    
	}
	public void litigationClaimSheet(DSRequest dsRequest, RPCManager rpc)  {
		  
		DSResponse dsResponse = new DSResponse();
		Session session = StandaloneDAO.sessionOpen();
		// Transaction tx=session.beginTransaction();
		try {
			rpc.doCustomResponse();

			dsResponse = dsRequest.execute();

			Map values = dsRequest.getValues();
			String fileName = values.get("fileName").toString();

			logger.debug("print " + fileName + " report is called");
			HttpServletResponse response = rpc.getContext().response;
			response.setHeader("content-disposition", "inline; filename=" + fileName + ".pdf");
			response.setContentType("application/pdf");

			  //form details
            List<Map> records=(List<Map>)values.get("records"); 
            Map form=(Map) values.get("form");
            
			String entityName = fileName.equals("Litigation Log Sheets") ? "LitigationMatter" : "Claim";

			if (records != null && records.size() > 0) {
				PDDocument printpdf = new PDDocument();

				List<com.legaledge.maestro.server.summary.LitClaimReports> litClaimReports = new ArrayList<com.legaledge.maestro.server.summary.LitClaimReports>();
				for (Map map : records) {
					if (map.get("id") != null && map.get("entityName") != null && map.get("entityName").toString().equals(entityName)) {
						com.legaledge.maestro.server.summary.LitClaimReports litClaim =
								(com.legaledge.maestro.server.summary.LitClaimReports) session.get(com.legaledge.maestro.server.summary.LitClaimReports.class,new Long(map.get("id").toString()));

						if (litClaim != null) {
							litClaimReports.add(litClaim);

						}
					}
				}
            	if(litClaimReports!=null && litClaimReports.size()>0){
            		new LitClaimReportsPrinting(PDRectangle.LETTER,litClaimReports,fileName,form).processReports(printpdf,session, dsRequest);
            	}

            	ServletOutputStream out = response.getOutputStream();  		
	            try(ByteArrayOutputStream output = new ByteArrayOutputStream()) {
	            	printpdf.save(output);
	                byte[] bytes = output.toByteArray();
	                out.write(bytes);
	                response.setContentLength((int) bytes.length);
	            }
	            printpdf.close();
	            dsResponse.setStatus(DSResponse.STATUS_SUCCESS);  
	            rpc.send(dsRequest, dsResponse);  
	            
	            logger.debug("Succcess");
            }
	        else
	        {
	            dsResponse.setStatus(DSResponse.STATUS_FAILURE);
	            rpc.sendFailure(dsRequest, "No Cause and Summary report Found");  
	        }
            
			  }catch (Exception e) {
        	dsResponse.setStatus(DSResponse.STATUS_FAILURE);
        	e.printStackTrace();
        	logger.debug("Failed  Printing Cause and Summary report");
        	try {  
                rpc.sendFailure(dsRequest, e.getMessage());  
            } catch(Exception er) {
            	e.printStackTrace();
            }  
        }  
        finally {
        	HibernateSessionFactory.closeSession(session);
        }
    
}

	private String getClaimLitigationClaimRecords(String entityName, Map generalFormValue, Map statusFormValue,Map litClaimFormValue, Map datesFormValue) {
		StringBuffer hql = new StringBuffer("from LitClaimReports lc where lc.entityName ='" + entityName + "'");

		// StatusForm
		if (!statusFormValue.isEmpty() && statusFormValue.size() > 0) {
			if (statusFormValue.get("caseStatus") != null && !statusFormValue.get("caseStatus").toString().equalsIgnoreCase("All")) {
				hql.append(" and caseStatus='" + statusFormValue.get("caseStatus") + "'");
			}
		}

		if (!generalFormValue.isEmpty() && generalFormValue.size() > 0) {
			if (generalFormValue.get("department") != null)
				hql.append(" and department='" + generalFormValue.get("department") + "'");
			if (generalFormValue.get("cause") != null)
				hql.append(" and cause='" + generalFormValue.get("cause") + "'");
		}

		if (!litClaimFormValue.isEmpty() && litClaimFormValue.size() > 0) {
			if (litClaimFormValue.get("type") != null)
				hql.append(" and type='" + litClaimFormValue.get("type") + "'");

			if (litClaimFormValue.get("category") != null)
				hql.append(" and category='" + litClaimFormValue.get("category") + "'");

			if (litClaimFormValue.get("dateFiledStart") != null && litClaimFormValue.get("dateFiledEnd") != null) {
					hql.append(" and dateFiled between '" + (Date) litClaimFormValue.get("dateFiledStart") + "' and '"+ litClaimFormValue.get("dateFiledEnd") + "'");
			}

			if (litClaimFormValue.get("dateServedStart") != null && litClaimFormValue.get("dateServedEnd") != null) {
				hql.append(" and dateServed between '" + (Date) litClaimFormValue.get("dateServedStart") + "' and '"+ litClaimFormValue.get("dateServedEnd") + "'");
		}

		}
		// DatesForm

		if (!datesFormValue.isEmpty() && datesFormValue.size() > 0) {
			// Date
				if (datesFormValue.get("dateOpenedStart") != null && datesFormValue.get("dateOpenedEnd") != null) {
					hql.append(" and dateOpened between '" + (Date) datesFormValue.get("dateOpenedStart") + "' and '"+ (Date) datesFormValue.get("dateOpenedEnd") + "'");
				}
			
				if (datesFormValue.get("incidentDateStart") != null && datesFormValue.get("incidentDateEnd") != null) {
					hql.append(" and incidentDate between '" + (Date) datesFormValue.get("incidentDateStart") + "' and '"+ (Date) datesFormValue.get("incidentDateEnd") + "'");
				}
			
				if (datesFormValue.get("dispositionDateStart") != null && datesFormValue.get("dispositionDateEnd") != null) {
					hql.append(" and dispositionDate between '" + (Date) datesFormValue.get("dispositionDateStart") + "' and '"+ (Date) datesFormValue.get("dispositionDateEnd") + "'");
				}
				
				if (datesFormValue.get("statusDateStart") != null && datesFormValue.get("statusDateEnd") != null) {
					hql.append(" and statusDate between '" + (Date) datesFormValue.get("statusDateStart") + "' and '"+ (Date) datesFormValue.get("statusDateEnd") + "'");
				}
		}
		return hql.toString();
	}
	
}
