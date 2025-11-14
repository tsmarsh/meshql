package com.meshql.server;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.security.Principal;
import java.util.*;

/**
 * Adapter that wraps a javax.servlet.http.HttpServletRequest to implement jakarta.servlet.http.HttpServletRequest
 */
public class ServletRequestAdapter implements HttpServletRequest {
    private final javax.servlet.http.HttpServletRequest delegate;

    public ServletRequestAdapter(javax.servlet.http.HttpServletRequest delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getAuthType() {
        return delegate.getAuthType();
    }

    @Override
    public Cookie[] getCookies() {
        javax.servlet.http.Cookie[] javaxCookies = delegate.getCookies();
        if (javaxCookies == null) return null;

        Cookie[] jakartaCookies = new Cookie[javaxCookies.length];
        for (int i = 0; i < javaxCookies.length; i++) {
            javax.servlet.http.Cookie jc = javaxCookies[i];
            jakartaCookies[i] = new Cookie(jc.getName(), jc.getValue());
        }
        return jakartaCookies;
    }

    @Override
    public long getDateHeader(String name) {
        return delegate.getDateHeader(name);
    }

    @Override
    public String getHeader(String name) {
        return delegate.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        return delegate.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return delegate.getHeaderNames();
    }

    @Override
    public int getIntHeader(String name) {
        return delegate.getIntHeader(name);
    }

    @Override
    public String getMethod() {
        return delegate.getMethod();
    }

    @Override
    public String getPathInfo() {
        return delegate.getPathInfo();
    }

    @Override
    public String getPathTranslated() {
        return delegate.getPathTranslated();
    }

    @Override
    public String getContextPath() {
        return delegate.getContextPath();
    }

    @Override
    public String getQueryString() {
        return delegate.getQueryString();
    }

    @Override
    public String getRemoteUser() {
        return delegate.getRemoteUser();
    }

    @Override
    public boolean isUserInRole(String role) {
        return delegate.isUserInRole(role);
    }

    @Override
    public Principal getUserPrincipal() {
        return delegate.getUserPrincipal();
    }

    @Override
    public String getRequestedSessionId() {
        return delegate.getRequestedSessionId();
    }

    @Override
    public String getRequestURI() {
        return delegate.getRequestURI();
    }

    @Override
    public StringBuffer getRequestURL() {
        return delegate.getRequestURL();
    }

    @Override
    public String getServletPath() {
        return delegate.getServletPath();
    }

    @Override
    public HttpSession getSession(boolean create) {
        return null; // Not implemented for simplicity
    }

    @Override
    public HttpSession getSession() {
        return null; // Not implemented for simplicity
    }

    @Override
    public String changeSessionId() {
        return null;
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return delegate.isRequestedSessionIdValid();
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return delegate.isRequestedSessionIdFromCookie();
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return delegate.isRequestedSessionIdFromURL();
    }

    @Override
    public boolean authenticate(HttpServletResponse response) {
        return false;
    }

    @Override
    public void login(String username, String password) {
    }

    @Override
    public void logout() {
    }

    @Override
    public Collection<Part> getParts() {
        return Collections.emptyList();
    }

    @Override
    public Part getPart(String name) {
        return null;
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) {
        return null;
    }

    @Override
    public Object getAttribute(String name) {
        return delegate.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return delegate.getAttributeNames();
    }

    @Override
    public String getCharacterEncoding() {
        return delegate.getCharacterEncoding();
    }

    @Override
    public void setCharacterEncoding(String env) {
        try {
            delegate.setCharacterEncoding(env);
        } catch (java.io.UnsupportedEncodingException e) {
            // Wrap in RuntimeException since jakarta.servlet interface doesn't declare IOException
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getContentLength() {
        return delegate.getContentLength();
    }

    @Override
    public long getContentLengthLong() {
        return delegate.getContentLengthLong();
    }

    @Override
    public String getContentType() {
        return delegate.getContentType();
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        final javax.servlet.ServletInputStream javaxInputStream = delegate.getInputStream();
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return javaxInputStream.isFinished();
            }

            @Override
            public boolean isReady() {
                return javaxInputStream.isReady();
            }

            @Override
            public void setReadListener(ReadListener readListener) {
            }

            @Override
            public int read() throws IOException {
                return javaxInputStream.read();
            }
        };
    }

    @Override
    public String getParameter(String name) {
        return delegate.getParameter(name);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return delegate.getParameterNames();
    }

    @Override
    public String[] getParameterValues(String name) {
        return delegate.getParameterValues(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return delegate.getParameterMap();
    }

    @Override
    public String getProtocol() {
        return delegate.getProtocol();
    }

    @Override
    public String getScheme() {
        return delegate.getScheme();
    }

    @Override
    public String getServerName() {
        return delegate.getServerName();
    }

    @Override
    public int getServerPort() {
        return delegate.getServerPort();
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return delegate.getReader();
    }

    @Override
    public String getRemoteAddr() {
        return delegate.getRemoteAddr();
    }

    @Override
    public String getRemoteHost() {
        return delegate.getRemoteHost();
    }

    @Override
    public void setAttribute(String name, Object o) {
        delegate.setAttribute(name, o);
    }

    @Override
    public void removeAttribute(String name) {
        delegate.removeAttribute(name);
    }

    @Override
    public Locale getLocale() {
        return delegate.getLocale();
    }

    @Override
    public Enumeration<Locale> getLocales() {
        return delegate.getLocales();
    }

    @Override
    public boolean isSecure() {
        return delegate.isSecure();
    }

    @Override
    public RequestDispatcher getRequestDispatcher(String path) {
        return null;
    }

    @Override
    public int getRemotePort() {
        return delegate.getRemotePort();
    }

    @Override
    public String getLocalName() {
        return delegate.getLocalName();
    }

    @Override
    public String getLocalAddr() {
        return delegate.getLocalAddr();
    }

    @Override
    public int getLocalPort() {
        return delegate.getLocalPort();
    }

    @Override
    public ServletContext getServletContext() {
        return null;
    }

    @Override
    public AsyncContext startAsync() {
        return null;
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) {
        return null;
    }

    @Override
    public boolean isAsyncStarted() {
        return false;
    }

    @Override
    public boolean isAsyncSupported() {
        return false;
    }

    @Override
    public AsyncContext getAsyncContext() {
        return null;
    }

    @Override
    public DispatcherType getDispatcherType() {
        return DispatcherType.REQUEST;
    }

    @Override
    public String getRequestId() {
        return null;
    }

    @Override
    public String getProtocolRequestId() {
        return null;
    }

    @Override
    public ServletConnection getServletConnection() {
        return null;
    }
}
