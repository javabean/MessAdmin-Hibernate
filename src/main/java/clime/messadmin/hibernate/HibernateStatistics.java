/**
 *
 */
package clime.messadmin.hibernate;

import java.io.IOException;
import java.io.PrintWriter;
import java.text.Format;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.Date;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.hibernate.HibernateException;
import org.hibernate.SessionFactory;
import org.hibernate.engine.SessionFactoryImplementor;
import org.hibernate.hql.QueryTranslator;
import org.hibernate.hql.ast.ASTQueryTranslatorFactory;
import org.hibernate.stat.Statistics;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import clime.messadmin.admin.AdminActionProvider;
import clime.messadmin.admin.BaseAdminActionWithContext;
import clime.messadmin.i18n.I18NSupport;
import clime.messadmin.model.Server;
import clime.messadmin.providers.spi.ApplicationDataProvider;
import clime.messadmin.providers.spi.ApplicationLifeCycleProvider;
import clime.messadmin.utils.DateUtils;
import clime.messadmin.utils.FastDateFormat;
import clime.messadmin.utils.StringUtils;

/**
 * Displays Hibernate 3.x {@link SessionFactory}'s statistics.<br />
 * This implementation uses Spring to fetch the {@link SessionFactory}.
 *
 * TODO allow to display statistics for more than 1 SessionFactory
 * TODO allow configuration to enable/disable Detailed metrics (lots of html generated!)
 * TODO Request-level Provider to record/display current Session's stats (in case of OpenSessionInView pattern)
 * TODO allow for non-Spring-managed SessionFactory
 *
 * @author C&eacute;drik LIME
 */
public class HibernateStatistics extends BaseAdminActionWithContext implements ApplicationDataProvider, AdminActionProvider, ApplicationLifeCycleProvider {
	private static final String BUNDLE_NAME = HibernateStatistics.class.getName();
	public static final String ACTION_ID = "HibernateStatistics";//$NON-NLS-1$
	public static final String HIBERNATE_ACTION_NAME = "HibernateAction";//$NON-NLS-1$
	public static final String HIBERNATE_ACTION_CLEAR_STATISTICS   = "clearStatistics";//$NON-NLS-1$
	public static final String HIBERNATE_ACTION_SET_STATISTICS_ON  = "statsOn";//$NON-NLS-1$
	public static final String HIBERNATE_ACTION_SET_STATISTICS_OFF = "statsOff";//$NON-NLS-1$

	/**
	 *
	 */
	public HibernateStatistics() {
		super();
	}

	/**
	 * Look up the SessionFactory that this filter should use.
	 * <p>Default implementation looks for a single bean of type {@link SessionFactory}
	 * in Spring's root application context.
	 * @return the SessionFactory to use
	 * @see #getSessionFactoryBeanName
	 */
	protected SessionFactory lookupSessionFactory(ServletContext servletContext) {
		WebApplicationContext wac =
				WebApplicationContextUtils.getRequiredWebApplicationContext(servletContext);
		SessionFactory sessionFactory = (SessionFactory) BeanFactoryUtils.beanOfTypeIncludingAncestors(wac, SessionFactory.class, false, true);
		return sessionFactory;
	}

	static String hql2sql(SessionFactoryImplementor sessionFactory, String hql) {
		if (hql == null || "".equals(hql.trim())) {
			return "";
		}
		try {
			QueryTranslator translator = new ASTQueryTranslatorFactory().createQueryTranslator(hql, hql, Collections.EMPTY_MAP, sessionFactory);
			translator.compile(Collections.EMPTY_MAP, false);
			return translator.getSQLString();
		} catch (HibernateException he) {
			return he.toString();
		} catch (LinkageError ignore) {// Don't ask: Hibernate 3.2.6 throws java.lang.NoSuchMethodError: org.hibernate.hql.antlr.HqlBaseParser.recover(Lantlr/RecognitionException;Lantlr/collections/impl/BitSet;)V
			return ignore.toString();
		}
	}

	/***********************************************************************/

	/** {@inheritDoc} */
	public void contextInitialized(ServletContext servletContext) {
		// Validate SessionFactory exists, and enable statistics if necessary
		SessionFactory sessionFactory = lookupSessionFactory(servletContext);
//		if (sessionFactory != null && ! sessionFactory.isClosed()) {
//			if (! sessionFactory.getStatistics().isStatisticsEnabled()) {
//				String sessionFactoryBeanName = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(wac, SessionFactory.class, false, true)[0];
//				servletContext.log("INFO MessAdmin enabled statistics for Hibernate SessionFactory \"" + sessionFactoryBeanName + '"');
//				sessionFactory.getStatistics().setStatisticsEnabled(true);
//			}
//		}
	}
	/** {@inheritDoc} */
	public void contextDestroyed(ServletContext servletContext) {
		// nothing
	}

	/***********************************************************************/

	/** {@inheritDoc} */
	@Override
	public int getPriority() {
		return 2000;
	}

	/***********************************************************************/

	/** {@inheritDoc} */
	public String getApplicationDataTitle(ServletContext context) {
		return I18NSupport.getLocalizedMessage(BUNDLE_NAME, I18NSupport.getClassLoader(context), "title");//$NON-NLS-1$
	}

	/** {@inheritDoc} */
	public String getXHTMLApplicationData(ServletContext context) {
		final ClassLoader cl = I18NSupport.getClassLoader(context);
		SessionFactory sessionFactory = lookupSessionFactory(context);
		if (sessionFactory == null) {
			return I18NSupport.getLocalizedMessage(BUNDLE_NAME, cl, "error.noSessionFactory");//$NON-NLS-1$
		}
		if (sessionFactory.isClosed()) {
			return I18NSupport.getLocalizedMessage(BUNDLE_NAME, cl, "error.sessionFactory.closed");//$NON-NLS-1$
		}
		final Statistics statistics = sessionFactory.getStatistics();
		NumberFormat numberFormatter = NumberFormat.getNumberInstance(I18NSupport.getAdminLocale());
		Format dateFormatter = FastDateFormat.getInstance(DateUtils.DEFAULT_DATE_TIME_FORMAT);
		final StringBuffer out = new StringBuffer(65536);

		final String baseHTMLid = this.getClass().getName() + '-';


		/* Enable / disable / clear statistics */

		out.append("<p>");
		if (statistics.isStatisticsEnabled()) {
			String urlDisableStats = getActionUrl(context, HIBERNATE_ACTION_SET_STATISTICS_OFF);
			out.append(buildActionLink(urlDisableStats, I18NSupport.getLocalizedMessage(BUNDLE_NAME, cl, "action.disableStats"), this));//$NON-NLS-1$
		} else {
			String urlEnableStats = getActionUrl(context, HIBERNATE_ACTION_SET_STATISTICS_ON);
			out.append(buildActionLink(urlEnableStats, I18NSupport.getLocalizedMessage(BUNDLE_NAME, cl, "action.enableStats"), this));//$NON-NLS-1$
		}
		out.append("&nbsp;|&nbsp;");
		String urlClearStats = getActionUrl(context, HIBERNATE_ACTION_CLEAR_STATISTICS);
		out.append(buildActionLink(urlClearStats, I18NSupport.getLocalizedMessage(BUNDLE_NAME, cl, "action.clearStatistics"), this));//$NON-NLS-1$
		out.append("</p>\n");

		if (! statistics.isStatisticsEnabled()) {
			return out.toString();//"Please set <code>hibernate.generate_statistics</code> to <code>true</code> in your Hibernate configuration.";
		}


		/* General Sessions metrics */

		out.append(I18NSupport.getLocalizedMessage(BUNDLE_NAME, cl, "session.title"));//$NON-NLS-1$
		out.append("\n<ul>");
		//out.append("<li>").append(I18NSupport.getLocalizedMessage("key.1", statistics.getSomeValue()).append("</li>"));
		appendStat(out, cl,                  "session.startTime",      StringUtils.escapeXml(dateFormatter.format(new Date(statistics.getStartTime()))));
		appendStat(out, cl, numberFormatter, "session.connectionsObtained", statistics.getConnectCount());
		appendStat(out, cl, numberFormatter, "session.sessionsOpened",       statistics.getSessionOpenCount());
		appendStat(out, cl, numberFormatter, "session.sessionsClosed",       statistics.getSessionCloseCount());
		appendStat(out, cl, numberFormatter, "session.flushes",             statistics.getFlushCount());
		appendStat(out, cl, numberFormatter, "session.transactions",         statistics.getTransactionCount());
		appendStat(out, cl, numberFormatter, "session.successfulTransactions", statistics.getSuccessfulTransactionCount());
		appendStat(out, cl, numberFormatter, "session.optimisticLockFailures", statistics.getOptimisticFailureCount());
		appendStat(out, cl, numberFormatter, "session.statementsPrepared",  statistics.getPrepareStatementCount());
		appendStat(out, cl, numberFormatter, "session.statementsClosed",    statistics.getCloseStatementCount());
		out.append("</ul>\n");


		/* Global metrics */

		out.append("<br/>\n").append(I18NSupport.getLocalizedMessage(BUNDLE_NAME, cl, "global.title")).append("\n<ul>");
		appendStat(out, cl, numberFormatter, "global.entitiesLoaded",    statistics.getEntityLoadCount());
		appendStat(out, cl, numberFormatter, "global.entitiesFetched",   statistics.getEntityFetchCount());
		appendStat(out, cl, numberFormatter, "global.entitiesInserted",  statistics.getEntityInsertCount());
		appendStat(out, cl, numberFormatter, "global.entitiesUpdated",   statistics.getEntityUpdateCount());
		appendStat(out, cl, numberFormatter, "global.entitiesDeleted",   statistics.getEntityDeleteCount());
		appendStat(out, cl, numberFormatter, "global.collectionsLoaded",    statistics.getCollectionLoadCount());
		appendStat(out, cl, numberFormatter, "global.collectionsFetched",   statistics.getCollectionFetchCount());
		appendStat(out, cl, numberFormatter, "global.collectionsRecreated", statistics.getCollectionRecreateCount());
		appendStat(out, cl, numberFormatter, "global.collectionsUpdated",   statistics.getCollectionUpdateCount());
		appendStat(out, cl, numberFormatter, "global.collectionsRemoved",   statistics.getCollectionRemoveCount());
		appendStat(out, cl, numberFormatter, "global.queriesExecutedToDatabase", statistics.getQueryExecutionCount());
		final double totalQueryCacheHitMissCount = Math.max(1, statistics.getQueryCacheHitCount()+statistics.getQueryCacheMissCount());
		appendStat(out, cl, numberFormatter, "global.queryCachePuts",            statistics.getQueryCachePutCount());
		appendStat(out, cl,                  "global.queryCacheHits",
				Long.valueOf(statistics.getQueryCacheHitCount()),
				new Double(statistics.getQueryCacheHitCount()/totalQueryCacheHitMissCount)
			);
		appendStat(out, cl,                  "global.queryCacheMisses",
				Long.valueOf(statistics.getQueryCacheMissCount()),
				new Double(statistics.getQueryCacheMissCount()/totalQueryCacheHitMissCount)
			);
		appendStat(out, cl, numberFormatter, "global.maxQueryTime",              statistics.getQueryExecutionMaxTime());
		appendStat(out, cl, "global.maxQueryTimeQuery.hql", "<code>"+StringUtils.escapeXml(statistics.getQueryExecutionMaxTimeQueryString())+"</code>");
		if (sessionFactory instanceof SessionFactoryImplementor) {
			appendStat(out, cl, "global.maxQueryTimeQuery.sql", "<code>"+StringUtils.escapeXml(hql2sql((SessionFactoryImplementor)sessionFactory, statistics.getQueryExecutionMaxTimeQueryString()))+"</code>");
		}
		final double totalSecondLevelCacheHitMissCount = Math.max(1, statistics.getSecondLevelCacheHitCount()+statistics.getSecondLevelCacheMissCount());
		appendStat(out, cl, numberFormatter, "global.secondLevelCachePuts",   statistics.getSecondLevelCachePutCount());
		appendStat(out, cl,                  "global.secondLevelCacheHits",
				Long.valueOf(statistics.getSecondLevelCacheHitCount()),
				new Double(statistics.getSecondLevelCacheHitCount()/totalSecondLevelCacheHitMissCount)
			);
		appendStat(out, cl,                  "global.secondLevelCacheMisses",
				Long.valueOf(statistics.getSecondLevelCacheMissCount()),
				new Double(statistics.getSecondLevelCacheMissCount()/totalSecondLevelCacheHitMissCount)
			);
		out.append("</ul>\n");


		/* Detailed metrics */

		out.append("<br/>\n").append(I18NSupport.getLocalizedMessage(BUNDLE_NAME, cl, "detail.title")).append("<br/>\n");

		// Entities
		String[] entityNames = statistics.getEntityNames();
		out.append("<fieldset>");
		out.append("<legend class=\"collapsible\" id=\"").append(baseHTMLid).append("entities").append("\">").append(I18NSupport.getLocalizedMessage(BUNDLE_NAME, cl, "detail.entities.title", numberFormatter.format(entityNames.length))).append("</legend>\n");
		out.append("<div id=\"").append(baseHTMLid).append("entities-target").append("\">");
		new EntityStatisticsTable().getXHTMLApplicationData(out, context, statistics);
//		dump(out, entityNames, new CallBack() {
//			/** {@inheritDoc} */
//			public String displayValue(String name) {
//				// Don't use statistics.getEntityStatistics(name).toString(), since
//				// org.hibernate.stat.EntityStatistics went from Class to Interface
//				// in Hibernate 3.5.0
//				return new EntityStatistics(statistics.getEntityStatistics(name)).toString();
//			}
//		});
		out.append("</div></fieldset>\n");

		// Collections
		String[] collectionRoleNames = statistics.getCollectionRoleNames();
		out.append("<fieldset>");
		out.append("<legend class=\"collapsible\" id=\"").append(baseHTMLid).append("CollectionRoles").append("\">").append(I18NSupport.getLocalizedMessage(BUNDLE_NAME, cl, "detail.collections.title", numberFormatter.format(collectionRoleNames.length))).append("</legend>\n");
		out.append("<div id=\"").append(baseHTMLid).append("CollectionRoles-target").append("\">");
		new CollectionStatisticsTable().getXHTMLApplicationData(out, context, statistics);
//		dump(out, collectionRoleNames, new CallBack() {
//			/** {@inheritDoc} */
//			public String displayValue(String name) {
//				// Don't use statistics.getCollectionStatistics(name).toString(), since
//				// org.hibernate.stat.CollectionStatistics went from Class to Interface
//				// in Hibernate 3.5.0
//				return new CollectionStatistics(statistics.getCollectionStatistics(name)).toString();
//			}
//		});
		out.append("</div></fieldset>\n");

		// Queries
		String[] queries = statistics.getQueries();
		out.append("<fieldset>");
		out.append("<legend class=\"collapsible\" id=\"").append(baseHTMLid).append("Queries").append("\">").append(I18NSupport.getLocalizedMessage(BUNDLE_NAME, cl, "detail.queries.title", numberFormatter.format(queries.length))).append("</legend>\n");
		out.append("<div id=\"").append(baseHTMLid).append("Queries-target").append("\">");
		new QueryStatisticsTable(sessionFactory).getXHTMLApplicationData(out, context, statistics);
//		dump(out, queries, new CallBack() {
//			/** {@inheritDoc} */
//			public String displayValue(String name) {
//				// Don't use statistics.getQueryStatistics(name).toString(), since
//				// org.hibernate.stat.QueryStatistics went from Class to Interface
//				// in Hibernate 3.5.0
//				return new QueryStatistics(statistics.getQueryStatistics(name)).toString();
//			}
//		});
		out.append("</div></fieldset>\n");

		// 2nd-level cache
		String[] secondLevelCacheRegionNames = statistics.getSecondLevelCacheRegionNames();
		out.append("<fieldset>");
		out.append("<legend class=\"collapsible\" id=\"").append(baseHTMLid).append("SecondLevelCacheRegion").append("\">").append(I18NSupport.getLocalizedMessage(BUNDLE_NAME, cl, "detail.secondLevelCaches.title", numberFormatter.format(secondLevelCacheRegionNames.length))).append("</legend>\n");
		out.append("<div id=\"").append(baseHTMLid).append("SecondLevelCacheRegion-target").append("\">");
		new SecondLevelCacheStatisticsTable().getXHTMLApplicationData(out, context, statistics);
//		dump(out, secondLevelCacheRegionNames, new CallBack() {
//			/** {@inheritDoc} */
//			public String displayValue(String name) {
//				// Don't use statistics.getSecondLevelCacheStatistics(name).toString(), since
//				// org.hibernate.stat.SecondLevelCacheStatistics went from Class to Interface
//				// in Hibernate 3.5.0
//				return new SecondLevelCacheStatistics(statistics.getSecondLevelCacheStatistics(name)).toString();
//			}
//		});
		out.append("</div></fieldset>\n");

		return out.toString();
	}

	private void appendStat(StringBuffer out, ClassLoader cl, NumberFormat formatter, String name, long value) {
		appendStat(out, cl, name, formatter.format(value));
	}
	private void appendStat(StringBuffer out, ClassLoader cl, String nameKey, Object... values) {
		out.append("<li>").append(I18NSupport.getLocalizedMessage(BUNDLE_NAME, cl, nameKey, values)).append("</li>");
	}

	private static abstract class CallBack {
		public CallBack() {}
		public String displayName(String name) {
			return "<code>" + StringUtils.escapeXml(name) + "</code>";
		}
		public abstract String displayValue(String name);
	}
	private void dump(StringBuffer out, String[] names, CallBack callBack) {
		out.append("<dl>");
		for (int i = 0; i < names.length; ++i) {
			String name = names[i];
			out.append("<dt>").append(callBack.displayName(name)).append("</dt>\n");
			out.append("<dd>").append(callBack.displayValue(name)).append("</dd>\n");
		}
		out.append("</dl>\n");
	}

	protected String getActionUrl(ServletContext context, String subAction) {
		String urlPrefix = new StringBuffer().append('?').append(ACTION_PARAMETER_NAME).append('=').append(getActionID())
			.append('&').append(HIBERNATE_ACTION_NAME).append('=').append(subAction)
			.append('&').append(CONTEXT_KEY).append('=').append(urlEncodeUTF8(Server.getInstance().getApplication(context).getApplicationInfo().getInternalContextPath()))
			.toString();
		return urlPrefix;
	}

	/***********************************************************************/

	/** {@inheritDoc} */
	public String getActionID() {
		return ACTION_ID;
	}

	protected void displayXHTMLApplicationData(HttpServletRequest request, HttpServletResponse response, String context) throws ServletException, IOException {
		// ensure we get a GET
		if (METHOD_POST.equals(request.getMethod())) {
			sendRedirect(request, response);
			return;
		}
		// display cache statistics
		String data = getXHTMLApplicationData(getServletContext(context));
		setNoCache(response);
		PrintWriter out = response.getWriter();
		out.print(data);
		out.flush();
		out.close();
	}

	/** {@inheritDoc} */
	@Override
	public void serviceWithContext(HttpServletRequest request, HttpServletResponse response, String context) throws ServletException, IOException {
		String hibernateAction = request.getParameter(HIBERNATE_ACTION_NAME);
		if (StringUtils.isBlank(hibernateAction)) {
			displayXHTMLApplicationData(request, response, context);
			return;
		}
		SessionFactory sessionFactory = lookupSessionFactory(getServletContext(context));
		if (sessionFactory == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND, "Can not find Hibernate SessionFactory");
			return;
		}
		if (sessionFactory.isClosed()) {
			response.sendError(HttpServletResponse.SC_CONFLICT, "Hibernate SessionFactory is closed. Statistics are not available.");
			return;
		}
		final ServletContext servletContext = getServletContext(context);
		final WebApplicationContext wac =
			WebApplicationContextUtils.getRequiredWebApplicationContext(servletContext);
		final String sessionFactoryBeanName = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(wac, SessionFactory.class, false, true)[0];
		final Statistics statistics = sessionFactory.getStatistics();
		assert statistics != null;
		if (HIBERNATE_ACTION_SET_STATISTICS_ON.equalsIgnoreCase(hibernateAction)) {
			servletContext.log("INFO MessAdmin enabled statistics for Hibernate SessionFactory \"" + sessionFactoryBeanName + '"');
			statistics.setStatisticsEnabled(true);
		} else if (HIBERNATE_ACTION_SET_STATISTICS_OFF.equalsIgnoreCase(hibernateAction)) {
			servletContext.log("INFO MessAdmin disabled statistics for Hibernate SessionFactory \"" + sessionFactoryBeanName + '"');
			statistics.setStatisticsEnabled(false);
		} else if (HIBERNATE_ACTION_CLEAR_STATISTICS.equalsIgnoreCase(hibernateAction)) {
			servletContext.log("INFO MessAdmin cleared statistics for Hibernate SessionFactory \"" + sessionFactoryBeanName + '"');
			statistics.clear();
		} else {
			response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Unknown parameter " + HIBERNATE_ACTION_NAME + " value: " + hibernateAction);
			return;
		}
		displayXHTMLApplicationData(request, response, context);
	}
}
