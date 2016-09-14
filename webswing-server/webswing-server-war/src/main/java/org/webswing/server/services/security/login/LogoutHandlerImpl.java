package org.webswing.server.services.security.login;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.subject.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.webswing.server.base.AbstractUrlHandler;
import org.webswing.server.base.UrlHandler;
import org.webswing.server.model.exception.WsException;
import org.webswing.server.services.security.LogoutTokenAdapter;
import org.webswing.server.services.security.api.AbstractWebswingUser;
import org.webswing.server.services.security.modules.SecurityModuleWrapper;

public class LogoutHandlerImpl extends AbstractUrlHandler implements LogoutHandler {
	private static final Logger log = LoggerFactory.getLogger(LogoutHandlerImpl.class);

	public LogoutHandlerImpl(UrlHandler parent) {
		super(parent);
	}

	@Override
	protected String getPath() {
		return "logout";
	}

	@Override
	public boolean serve(HttpServletRequest req, HttpServletResponse res) throws WsException {
		try {
			logout(req, res);
		} catch (Exception e) {
			log.error("Failed to logout", e);
			throw new WsException("Failed to logout", e);
		}
		
		SecurityModuleWrapper securityModuleWrapper = getSecurityProvider().get();
		try {
			securityModuleWrapper.doLogout(req, res);
		} catch (Exception e) {
			log.error("Failed Logout by SecurityModule.", e);
			throw new WsException("Failed Logout by SecurityModule.", e);
		}
		return true;
	}

	protected void logout(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		AbstractWebswingUser user = getUser();
		if (user != null) {
			Subject subject = SecurityUtils.getSubject();
			try {
				//logout only user for the secured path (in case other users are logged in the same session)
				subject.login(new LogoutTokenAdapter(getSecuredPath(), user));
			} catch (AuthenticationException e) {
				//there was no user left in the session, so we can do full log out.
				subject.logout();
			}
		}
	}
}