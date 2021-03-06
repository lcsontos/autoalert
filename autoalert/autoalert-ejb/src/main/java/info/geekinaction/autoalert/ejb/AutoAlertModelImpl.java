/*
 * Copyright (C) 2010 - present, Laszlo Csontos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

/**
 * 
 */
package info.geekinaction.autoalert.ejb;

import static info.geekinaction.autoalert.common.CommonConstants.ISO_TIMESTAMP_FORMAT;

import static info.geekinaction.autoalert.ejb.AutoAlertQuery.FIND_DATABASE;
import static info.geekinaction.autoalert.ejb.AutoAlertQuery.FIND_DATAFILES;
import static info.geekinaction.autoalert.ejb.AutoAlertQuery.FIND_INSTANCE_CPU_USAGE;
import static info.geekinaction.autoalert.ejb.AutoAlertQuery.FIND_INSTANCE_IO_USAGE;
import static info.geekinaction.autoalert.ejb.AutoAlertQuery.FIND_SESSION;
import static info.geekinaction.autoalert.ejb.AutoAlertQuery.FIND_SESSION_CPU_USAGE;
import static info.geekinaction.autoalert.ejb.AutoAlertQuery.FIND_SESSION_IO_USAGE;
import static info.geekinaction.autoalert.ejb.AutoAlertQuery.FIND_TABLESPACES;

import static info.geekinaction.autoalert.ejb.EJBConstants.AUTOALERT_MODEL_JNDI;
import static info.geekinaction.autoalert.ejb.EJBConstants.AUTOALERT_MODEL_NAME;
import static info.geekinaction.autoalert.ejb.EJBConstants.TIMER_NAME;
import static info.geekinaction.autoalert.ejb.EJBConstants.TIMER_INTERVAL;
import static info.geekinaction.autoalert.ejb.EJBConstants.TIME_SLICE;
import static info.geekinaction.autoalert.ejb.EJBConstants.TIME_SLICE_MSEC;
import static info.geekinaction.autoalert.ejb.EJBConstants.TIME_SLICE_SEC;

import static info.geekinaction.autoalert.model.domain.ParameterName.AUTOALERT_MAIL_FROM;
import static info.geekinaction.autoalert.model.domain.ParameterName.AUTOALERT_RCPT_TO;
import static info.geekinaction.autoalert.model.domain.ParameterName.AUTOALERT_SUBJECT;

import static java.util.Calendar.MILLISECOND;
import static java.util.Calendar.MINUTE;
import static java.util.Calendar.SECOND;

import info.geekinaction.autoalert.common.AbstractBusinessObject;
import info.geekinaction.autoalert.common.util.DateUtil;
import info.geekinaction.autoalert.common.util.LogUtil;
import info.geekinaction.autoalert.common.util.MailUtil;
import info.geekinaction.autoalert.jmx.IAutoAlertManagement;
import info.geekinaction.autoalert.mail.VelocityLogger;
import info.geekinaction.autoalert.model.domain.Database;
import info.geekinaction.autoalert.model.domain.Datafile;
import info.geekinaction.autoalert.model.domain.InstanceCpuUsage;
import info.geekinaction.autoalert.model.domain.InstanceIoUsage;
import info.geekinaction.autoalert.model.domain.Parameter;
import info.geekinaction.autoalert.model.domain.ParameterName;
import info.geekinaction.autoalert.model.domain.ParameterScope;
import info.geekinaction.autoalert.model.domain.Session;
import info.geekinaction.autoalert.model.domain.SessionCpuUsage;
import info.geekinaction.autoalert.model.domain.SessionIoUsage;
import info.geekinaction.autoalert.model.domain.Tablespace;
import info.geekinaction.autoalert.model.incident.AutoAlertIncident;
import info.geekinaction.autoalert.model.incident.AutoAlertIncidentListener;
import info.geekinaction.autoalert.model.incident.IAutoAlertIncidentHandler;
import info.geekinaction.autoalert.model.service.IAutoAlertModel;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.Local;
import javax.ejb.SessionContext;
import javax.ejb.Stateless;
import javax.ejb.Timeout;
import javax.ejb.Timer;
import javax.ejb.TimerService;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.interceptor.Interceptors;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * 
 * THIS HEART OF THE SYSTEM
 * 
 * Business logic implementation.
 * 
 * @author lcsontos
 * 
 */
@Stateless(name = AUTOALERT_MODEL_NAME, mappedName = AUTOALERT_MODEL_JNDI)
@Local({ IAutoAlertModel.class, IAutoAlertManagement.class, IAutoAlertIncidentHandler.class })
@Interceptors({ AutoAlertAuditInterceptor.class })
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class AutoAlertModelImpl extends AbstractBusinessObject implements IAutoAlertModel, IAutoAlertManagement, IAutoAlertIncidentHandler {
	
	private static final Logger logger = Logger.getLogger(AutoAlertModelImpl.class);
	
	private static final int SESSION_TOP_N = 10;

	/**
	 * Persistence context.
	 */
	@PersistenceContext(unitName = "AutoAlertPU")
	private EntityManager em;

	/**
	 * Mail session
	 */
	// @Resource(name = "mail/aaSession")
	private javax.mail.Session mailSession;
	
	/**
	 * System wide parameters.
	 */
	private static Map<ParameterName, Parameter> systemParameters;
	
	///// EJB lifecycle methods. /////
	
	@PostConstruct
	@Override
	public void init() {
		logger.debug(this.toString() + " initialized.");
	}
	
	@PreDestroy
	@Override
	public void destroy() {
		logger.debug(this.toString() + " destroyed.");
	}
	
	/**
	 * 
	 */
	@Resource
	@Override
	protected void setSessionContext(SessionContext sessionContext) {
		this.sessionContext = sessionContext;
	}

	/**
	 * @see info.geekinaction.autoalert.model.service.IAutoAlertModel#findParameters()
	 */
	public Map<ParameterName, Parameter> findParameters() {
		if (systemParameters == null) {
			reloadConfiguration();
		}
		// Return parameter map.
		return systemParameters;
	}
	
	/**
	 * @see info.geekinaction.autoalert.model.service.IAutoAlertModel#findDatabase()
	 */
	public Database findDatabase() {
		Query query = createQuery(FIND_DATABASE);
		Database database = (Database) query.getSingleResult();
		return database;
	}

	/**
	 * @see info.geekinaction.autoalert.model.service.IAutoAlertModel#findDatafiles(boolean)
	 */
	public List<Datafile> findDatafiles(boolean alertsOnly) {
		int alertParam = alertsOnly ? 0 : -1;

		Query query = createQuery(FIND_DATAFILES);
		query.setParameter(1, alertParam);

		List<Datafile> retval = (List<Datafile>) query.getResultList();
		return retval;
	}

	/**
	 * @see info.geekinaction.autoalert.model.service.IAutoAlertModel#findInstanceCpuUsage(boolean)
	 */
	public List<InstanceCpuUsage> findInstanceCpuUsage(boolean alertsOnly) {
		int alertParam = alertsOnly ? 0 : -1;

		Query query = createQuery(FIND_INSTANCE_CPU_USAGE);
		query.setParameter(1, alertParam);
		query.setParameter(2, alertParam);

		List<InstanceCpuUsage> retval = (List<InstanceCpuUsage>) query.getResultList();
		return retval;
	}

	/**
	 * @see info.geekinaction.autoalert.model.service.IAutoAlertModel#findInstanceIoUsage(boolean)
	 */
	public List<InstanceIoUsage> findInstanceIoUsage(boolean alertsOnly) {
		int alertParam = alertsOnly ? 0 : -1;

		Query query = createQuery(FIND_INSTANCE_IO_USAGE);
		query.setParameter(1, alertParam);
		query.setParameter(2, alertParam);

		List<InstanceIoUsage> retval = (List<InstanceIoUsage>) query.getResultList();
		return retval;
	}

	/**
	 * @see info.geekinaction.autoalert.model.service.IAutoAlertModel#findSession()
	 */
	public List<Session> findSession() {
		Query query = createQuery(FIND_SESSION);
		List<Session> retval = (List<Session>) query.getResultList();
		return retval;
	}

	/**
	 * @see info.geekinaction.autoalert.model.service.IAutoAlertModel#findSessionCpuUsage()
	 */
	public List<SessionCpuUsage> findSessionCpuUsage() {
		Query query = createQuery(FIND_SESSION_CPU_USAGE);
		query.setFirstResult(0);
		query.setMaxResults(SESSION_TOP_N);
		List<SessionCpuUsage> retval = (List<SessionCpuUsage>) query.getResultList();
		return retval;
	}

	/**
	 * @see info.geekinaction.autoalert.model.service.IAutoAlertModel#findSessionIoUsage()
	 */
	public List<SessionIoUsage> findSessionIoUsage() {
		Query query = createQuery(FIND_SESSION_IO_USAGE);
		query.setFirstResult(0);
		query.setMaxResults(SESSION_TOP_N);
		List<SessionIoUsage> retval = (List<SessionIoUsage>) query.getResultList();
		return retval;
	}

	/**
	 * @see info.geekinaction.autoalert.model.service.IAutoAlertModel#findTablespaces(boolean)
	 */
	public List<Tablespace> findTablespaces(boolean alertsOnly) {
		int alertParam = alertsOnly ? 0 : -1;

		Query query = createQuery(FIND_TABLESPACES);
		query.setParameter(1, alertParam);

		List<Tablespace> retval = (List<Tablespace>) query.getResultList();
		return retval;
	}

	/**
	 * @see info.geekinaction.autoalert.jmx.IAutoAlertManagement#reloadConfiguration()
	 */
	@Override
	public void reloadConfiguration() {
		try {
			// If this method is called for the first time the parameter map does no yet exists.
			if (systemParameters == null) {
				systemParameters = new  ConcurrentHashMap<ParameterName, Parameter>();
			}
			
			// Query parameters from the database.
			Query query = createQuery(AutoAlertQuery.FIND_PARAMETERS);
			List<Parameter> params = (List<Parameter>) query.getResultList();
			
			// Convert to proper collection format.
			for (Parameter param : params) {
				// Name of the current parameter.
				ParameterName pname = ParameterName.valueOf(param.getParamName());
				systemParameters.put(pname, param);
			}
		} catch (RuntimeException e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	/**
	 * @see info.geekinaction.autoalert.jmx.IAutoAlertManagement#getParameter()
	 */
	@Override
	@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
	public String getParameter(String paramName) {
		try {
			// Find the parameter name in the enum.
			ParameterName parameterName = ParameterName.valueOf(paramName);
			
			// If there is no such parameter in the map.
			Parameter parameter = systemParameters.get(parameterName);
			if (parameter == null) {
				return null;
			}
			
			// Prepare for returning the value
			String retval = null;
			if ("V".equals(parameter.getParamType())) {
				retval = Parameter.getParameterAsString(systemParameters, parameterName);
			} else if ("N".equals(parameter.getParamType())) {
				retval = Parameter.getParameterAsInteger(systemParameters, parameterName).toString();
			} else {
				// parameter.getParamType() must be either "V" or "N".
				throw new IllegalStateException("Invalid parameter type");
			}
			
			return retval;
		} catch (RuntimeException e) {
			logger.error(e.getMessage(), e);
			return null;
		}
	}
	
	/**
	 * @see info.geekinaction.autoalert.jmx.IAutoAlertManagement#setParameter()
	 */
	@Override
	@TransactionAttribute(TransactionAttributeType.REQUIRED)
	public void setParameter(String paramName, String parameterScope, String value) {
		try {
			
			// Find the parameter name in the enum.
			ParameterName parameterName = ParameterName.valueOf(paramName);
			ParameterScope _parameterScope = ParameterScope.valueOf(parameterScope);
			
			// If the given parameter does not exists in the initial map, that parameter is invalid.
			if (!systemParameters.containsKey(parameterName)) {
				throw new IllegalArgumentException("Such a parameter does not exists.");
			}
			
			// Get the current value.
			Parameter parameter = systemParameters.get(parameterName);
			
			// Determine how to store the new value;
			switch(_parameterScope) {
			// Both in DB and MEMORY
			case BOTH:
			// Just temporarily in memory.
			case MEMORY:
				parameter.setValue(value);
				systemParameters.put(parameterName, parameter);
				if (_parameterScope.equals(ParameterScope.MEMORY)) {
					break;
				}
			// Just in the DB.
			case DATABASE:
				parameter = em.find(Parameter.class, parameterName.name());
				parameter.setValue(value);
				em.merge(parameter);
			}
		} catch (RuntimeException e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	/**
	 * @see info.geekinaction.autoalert.model.incident.IAutoAlertIncidentHandler#storeIncident(AutoAlertIncident)
	 */
	@Override
	@TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
	public boolean storeIncident(AutoAlertIncident incident) {
		// Calculate time frame.
		Calendar calendar = Calendar.getInstance();
		// TODO Make this a system wide parameter.
		calendar.add(Calendar.MINUTE, -24 * 60);

		// Calculate a checkSum for the incident.
		int checkSum = new AutoAlertIncidentListener().createCheckSum(incident);
		Query query = createQuery(AutoAlertQuery.COUNT_INCIDENTS);
		query.setParameter(1, checkSum); // Checksum
		query.setParameter(2, calendar.getTime());
		
		// Lets see if there were similar incidents in the past like this.
		int result = ((Number) query.getSingleResult()).intValue();
		boolean similarIncidentExists = result > 0;
		
		// If not store this incident.
		if (!similarIncidentExists) {
			em.persist(incident);
		}
		
		return similarIncidentExists;
	}
	
	/**
	 * INTERNAL USE ONLY
	 * 
	 * As storeIncident() has been declared to create a NEW transaction,
	 * automatic transaction handling functionality of the JTA engine could not be exploited
	 * unless we call storeIncident() throught the container.  
	 * 
	 * @param incident
	 * @return
	 */
	private boolean storeIncident0(AutoAlertIncident incident) {
		// Acquire a proxy to this EJB object.
		IAutoAlertIncidentHandler handler = sessionContext.getBusinessObject(IAutoAlertIncidentHandler.class);
		return handler.storeIncident(incident);
	}

	/**
	 * 
	 * @param query
	 * @return
	 */
	private Query createQuery(AutoAlertQuery query) {
		String queryName = query.getQueryName();
		return em.createNamedQuery(queryName);

	}

	/**
	 * Finds our scheduled timer.
	 * 
	 * @return A scheduled timer of NULL if there is no such timer.
	 */
	@SuppressWarnings("unchecked")
	private Timer findTimer() {
		TimerService timerService = sessionContext.getTimerService();
		Collection<Timer> timers = (Collection<Timer>) timerService.getTimers();
		for (Iterator<Timer> iterator = timers.iterator(); iterator.hasNext();) {
			Timer timer = iterator.next();
			Serializable info = timer.getInfo();
			if (info.equals(TIMER_NAME)) {
				return timer;
			}
		}
		return null;
	}
	
	/**
	 * @see IAutoAlertManagement#startScheduler()
	 */
	@Override
	public void startScheduler() {
		TimerService timerService = sessionContext.getTimerService();
		
		// Find a former timer first
		if (findTimer() != null) {
			LogUtil.log(this, Level.WARN, "startScheduler(): Timer {0} has already been scheduled.", new Object[] { TIMER_NAME });
			return;
		}

		// Start time
		Calendar startTime = Calendar.getInstance();

		/*
		 * Give the RDBMS 5 minutes to gather its internal statistics for the
		 * first time. Thereafter they will be check in every minute.
		 */
		int min = startTime.get(MINUTE);
		min = (min / TIME_SLICE) * TIME_SLICE;
		startTime.set(MINUTE, min);
		startTime.add(MINUTE, TIME_SLICE + 1);
		startTime.set(SECOND, TIME_SLICE_SEC);
		startTime.set(MILLISECOND, TIME_SLICE_MSEC);

		// Create timer
		timerService.createTimer(startTime.getTime(), TIMER_INTERVAL, TIMER_NAME);

		Object[] msgParams = new Object[] { DateUtil.toChar(startTime.getTime(), ISO_TIMESTAMP_FORMAT), new Integer(TIMER_INTERVAL) };
		LogUtil.log(this, Level.INFO, "startScheduler(): Timer has been set. Start time: {0}, interval: {1} msec.", msgParams);
	}
	
	/**
	 * @see IAutoAlertManagement#stopScheduler()
	 */
	@Override
	public void stopScheduler() {
		Timer timer = findTimer();
		if (timer == null) {
			LogUtil.log(this, Level.WARN, "stopScheduler(): Timer {0} has not been found.", new Object[] { TIMER_NAME });
			return;
		}
		timer.cancel();
		LogUtil.log(this, Level.INFO, "stopScheduler(): Timer {0} has already been canceled.", new Object[] { TIMER_NAME });
	}
	
	/**
	 * @see IAutoAlertManagement#triggerScheduler()
	 */
	@Override
	public void triggerScheduler() {
		Timer timer = findTimer();
		if (timer == null) {
			LogUtil.log(this, Level.WARN, "triggerScheduler(): Timer {0} has not been found.", new Object[] { TIMER_NAME });
			return;
		}
		timerHandle(timer);
		LogUtil.log(this, Level.INFO, "triggerScheduler(): Timer {0} has been manually triggered.", new Object[] { TIMER_NAME });
	}

	/**
	 * THIS METHOD IS INTENDED TO BE CALLED BY THE EJB CONTAINER ONLY
	 * 
	 * timerHandle() gets executed when the scheduled timer expires.
	 * 
	 * @param timer A timer object which is passed by the container upon timer expiration.
	 */
	@Timeout
	@TransactionAttribute(TransactionAttributeType.REQUIRED)
	public void timerHandle(Timer timer) {

		try {
			// Log next timeout
			Date next = timer.getNextTimeout();
			LogUtil.log(this, Level.DEBUG, "timerHandle(): Timer {1} next expiration will be: {0}.", new Object[] { TIMER_NAME, DateUtil.toChar(next, ISO_TIMESTAMP_FORMAT) });

			//////////////////////
			// Check for alerts.
			//////////////////////

			// Info about RDBMS instance
			Database database = findDatabase();

			// Storage
			List<Tablespace> tablespaces = findTablespaces(true);
			List<Datafile> datafiles = findDatafiles(true);

			// Resources
			List<InstanceCpuUsage> cpuUsageList = findInstanceCpuUsage(true);
			List<InstanceIoUsage> ioUsageList = findInstanceIoUsage(true);
			InstanceCpuUsage cpuUsage = cpuUsageList.size() > 0 ? cpuUsageList.get(0) : null;
			InstanceIoUsage ioUsage = ioUsageList.size() > 0 ? ioUsageList.get(0) : null;

			// Assable alert message
			if (tablespaces.size() > 0 || cpuUsage != null || ioUsage != null) {
				
				AutoAlertIncident autoAlertIncident = new AutoAlertIncident(database, tablespaces, datafiles, cpuUsage, ioUsage);
				
				// If such an incident has not been detected before send and E-mail.
				boolean similarIncidentExists = storeIncident0(autoAlertIncident);
				if (!similarIncidentExists) {
					
					// Message body
					String message = VelocityLogger.initVelocity().createMessage(autoAlertIncident);

					// Get sender, recipients and subject
					Map<ParameterName, Parameter> parameters = findParameters();
					String from = Parameter.getParameterAsString(parameters, AUTOALERT_MAIL_FROM);
					String rcptCsv = Parameter.getParameterAsString(parameters, AUTOALERT_RCPT_TO);
					String[] rcpts = rcptCsv.split(",");
					String _subject = Parameter.getParameterAsString(parameters, AUTOALERT_SUBJECT);

					/*
					 * Subject
					 * 
					 *  [SIDNAME] Automatic Alert
					 *  
					 */
					StringBuilder subject = new StringBuilder();
					subject.append('['); subject.append(database.getDbUniqueName()); subject.append(']');
					subject.append(' ');
					subject.append(_subject);
					
					// Send alert message
					MailUtil.sendMessage(mailSession, from, rcpts, subject.toString(), message);				
				}
			}
			
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}

	}

}
