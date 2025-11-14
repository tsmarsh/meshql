package com.meshql.server;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Locale;

/**
 * Adapter that wraps a javax.servlet.http.HttpServletResponse to implement jakarta.servlet.http.HttpServletResponse
 */
public class ServletResponseAdapter implements HttpServletResponse {
    private final javax.servlet.http.HttpServletResponse delegate;

    public ServletResponseAdapter(javax.servlet.http.HttpServletResponse delegate) {
        this.delegate = delegate;
    }

    @Override
    public void addCookie(Cookie cookie) {
        javax.servlet.http.Cookie javaxCookie = new javax.servlet.http.Cookie(cookie.getName(), cookie.getValue());
        delegate.addCookie(javaxCookie);
    }

    @Override
    public boolean containsHeader(String name) {
        return delegate.containsHeader(name);
    }

    @Override
    public String encodeURL(String url) {
        return delegate.encodeURL(url);
    }

    @Override
    public String encodeRedirectURL(String url) {
        return delegate.encodeRedirectURL(url);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        delegate.sendError(sc, msg);
    }

    @Override
    public void sendError(int sc) throws IOException {
        delegate.sendError(sc);
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        delegate.sendRedirect(location);
    }

    @Override
    public void setDateHeader(String name, long date) {
        delegate.setDateHeader(name, date);
    }

    @Override
    public void addDateHeader(String name, long date) {
        delegate.addDateHeader(name, date);
    }

    @Override
    public void setHeader(String name, String value) {
        delegate.setHeader(name, value);
    }

    @Override
    public void addHeader(String name, String value) {
        delegate.addHeader(name, value);
    }

    @Override
    public void setIntHeader(String name, int value) {
        delegate.setIntHeader(name, value);
    }

    @Override
    public void addIntHeader(String name, int value) {
        delegate.addIntHeader(name, value);
    }

    @Override
    public void setStatus(int sc) {
        delegate.setStatus(sc);
    }

    @Override
    public int getStatus() {
        return delegate.getStatus();
    }

    @Override
    public String getHeader(String name) {
        return delegate.getHeader(name);
    }

    @Override
    public Collection<String> getHeaders(String name) {
        return delegate.getHeaders(name);
    }

    @Override
    public Collection<String> getHeaderNames() {
        return delegate.getHeaderNames();
    }

    @Override
    public String getCharacterEncoding() {
        return delegate.getCharacterEncoding();
    }

    @Override
    public String getContentType() {
        return delegate.getContentType();
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        final javax.servlet.ServletOutputStream javaxOutputStream = delegate.getOutputStream();
        return new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return javaxOutputStream.isReady();
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
            }

            @Override
            public void write(int b) throws IOException {
                javaxOutputStream.write(b);
            }
        };
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        return delegate.getWriter();
    }

    @Override
    public void setCharacterEncoding(String charset) {
        delegate.setCharacterEncoding(charset);
    }

    @Override
    public void setContentLength(int len) {
        delegate.setContentLength(len);
    }

    @Override
    public void setContentLengthLong(long len) {
        delegate.setContentLengthLong(len);
    }

    @Override
    public void setContentType(String type) {
        delegate.setContentType(type);
    }

    @Override
    public void setBufferSize(int size) {
        delegate.setBufferSize(size);
    }

    @Override
    public int getBufferSize() {
        return delegate.getBufferSize();
    }

    @Override
    public void flushBuffer() throws IOException {
        delegate.flushBuffer();
    }

    @Override
    public void resetBuffer() {
        delegate.resetBuffer();
    }

    @Override
    public boolean isCommitted() {
        return delegate.isCommitted();
    }

    @Override
    public void reset() {
        delegate.reset();
    }

    @Override
    public void setLocale(Locale loc) {
        delegate.setLocale(loc);
    }

    @Override
    public Locale getLocale() {
        return delegate.getLocale();
    }
}
