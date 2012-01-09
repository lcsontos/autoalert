/**
 * 
 */
package info.geekinaction.autoalert.mail;

import static info.geekinaction.autoalert.mail.MailConstants.MAIL_TEMPLATE;
import static info.geekinaction.autoalert.mail.MailConstants.VELOCITY_CONFIG_FILE;
import static info.geekinaction.autoalert.mail.MailConstants.VM_CPU_USAGE;
import static info.geekinaction.autoalert.mail.MailConstants.VM_DATABASE;
import static info.geekinaction.autoalert.mail.MailConstants.VM_DATAFILES;
import static info.geekinaction.autoalert.mail.MailConstants.VM_IO_USAGE;
import static info.geekinaction.autoalert.mail.MailConstants.VM_TABLESPACES;

import static org.apache.log4j.Level.ERROR;
import static org.apache.log4j.Level.INFO;
import static org.apache.log4j.Level.WARN;

import info.geekinaction.autoalert.model.incident.AutoAlertIncident;

import java.io.InputStream;
import java.io.StringWriter;
import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.log.LogChute;

/**
 * @author lcsontos
 * 
 */
public class VelocityHelper implements LogChute {
	
	private static final Logger logger = Logger.getLogger(VelocityHelper.class);

	private static VelocityHelper me = null;
	
	/**
	 * Initialize velocity framework.
	 */
	private VelocityHelper() {
		try {
			// Load configuration
			InputStream is = getClass().getResourceAsStream(VELOCITY_CONFIG_FILE);
			Properties config = new Properties();
			config.load(is);
			
			// INIT
			Velocity.init(config);
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
		}
	}

	/**
	 * @see org.apache.velocity.runtime.log.LogChute#init(org.apache.velocity.runtime.RuntimeServices)
	 */
	public void init(RuntimeServices runtimeServices) throws Exception {
		// According to velocity's documentation this method should be left alone...
	}

	/**
	 * @see org.apache.velocity.runtime.log.LogChute#isLevelEnabled(int)
	 */
	public boolean isLevelEnabled(int level) {
		// INFO is the finest log level we enable.
		return level > 0;
	}

	/**
	 * Log valocity's message with LOG4J API calls. 
	 * 
	 * @see org.apache.velocity.runtime.log.LogChute#log(int, java.lang.String)
	 */
	public void log(int level, String message) {
		log(level, message, null);
	}

	/**
	 * Log valocity's message with LOG4J API calls.
	 * 
	 * @see org.apache.velocity.runtime.log.LogChute#log(int, java.lang.String,
	 * java.lang.Throwable)
	 */
	public void log(int level, String message, Throwable throwable) {

		// Convert velocity's log level.
		Level _level = null;
		switch (level) {
		case INFO_ID:
			_level = INFO;
			break;
		case ERROR_ID:
			_level = ERROR;
			break;
		case WARN_ID:
			_level = WARN;
			break;
		default:
			_level = INFO;
		}
		
		logger.log(_level, message, throwable);
	}

	/**
	 * 
	 * @param autoAlertIncident
	 * @return
	 */
	public String createMessage(AutoAlertIncident autoAlertIncident) throws Exception {
		Template t = Velocity.getTemplate(MAIL_TEMPLATE);
		VelocityContext context = new VelocityContext();
		context.put(VM_DATABASE, autoAlertIncident.getDatabase());
		context.put(VM_TABLESPACES, autoAlertIncident.getTablespaces());
		context.put(VM_DATAFILES, autoAlertIncident.getDatafiles());
		context.put(VM_CPU_USAGE, autoAlertIncident.getCpuUsage());
		context.put(VM_IO_USAGE, autoAlertIncident.getIoUsage());

		// Render
		StringWriter message = new StringWriter();
		t.merge(context, message);
		
		return message.toString();
	}
	
	/**
	 * Singleton initializator.
	 */
	public static VelocityHelper initVelocity() {
		if (me == null) {
			me = new VelocityHelper();
		}
		return me;
	}

}