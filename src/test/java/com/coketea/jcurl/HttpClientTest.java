package com.coketea.jcurl;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by Johnson Wong on 2017/11/8.
 */
public class HttpClientTest {

    private static final String HTTP_RESPONSE_CHUNKED_CONTENT = "HTTP/1.1 200 OK\r\n"+
            "X-Powered-By: Servlet/3.1\r\n"+
            "Content-Type: text/html; charset=utf-8\r\n"+
            "Content-Language: zh-CN\r\n"+
            "Transfer-Encoding: chunked\r\n"+
            "Date: Wed, 08 Nov 2017 01:04:40 GMT\r\n"+
            "\r\n"+
            "14\r\n"+
            "{\"resultFlag\":\"0\",\"r\r\n" +
            "21\r\n" +
            "esultList\":[],\"recordsTotal\":\"0\"}\r\n"+
            "0\r\n";

    private static final String HTTP_RESPONSE_NOT_CHUNKED_CONTENT = "HTTP/1.1 200 OK\r\n"+
            "X-Powered-By: Servlet/3.1\r\n"+
            "Content-Type: text/html; charset=utf-8\r\n"+
            "Content-Language: zh-CN\r\n"+
            "Content-Length: 53\r\n"+
            "Date: Wed, 08 Nov 2017 01:04:40 GMT\r\n"+
            "\r\n"+
            "{\"resultFlag\":\"0\",\"resultList\":[],\"recordsTotal\":\"0\"}";

    private InputStream chunkedInputStream = null;

    private InputStream notChunkedInputStream = null;

    @Before
    public void init() {
        try {
            chunkedInputStream = new ByteArrayInputStream(HTTP_RESPONSE_CHUNKED_CONTENT.getBytes("UTF-8"));
            notChunkedInputStream = new ByteArrayInputStream(HTTP_RESPONSE_NOT_CHUNKED_CONTENT.getBytes("UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testReadLine() throws NoSuchMethodException,
            InvocationTargetException, IllegalAccessException, UnsupportedEncodingException {
        Class[] parameterTypes = { InputStream.class };
        Object[] arguments = {chunkedInputStream};
        HttpClient httpClient = new HttpClient();
        Method method = httpClient.getClass().getDeclaredMethod("readLine", parameterTypes);
        method.setAccessible(true);
        byte[] bytes = (byte[])method.invoke(httpClient, arguments);
        method.setAccessible(false);
        Assert.assertEquals(new String(bytes, "UTF-8"), "HTTP/1.1 200 OK");
    }

    private void parseResponseStatus(InputStream is) throws UnsupportedEncodingException,
            NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Class[] parameterTypes = { InputStream.class };
        Object[] arguments = { is };
        HttpClient httpClient = new HttpClient();
        Method method = httpClient.getClass().getDeclaredMethod("parseResponseStatus", parameterTypes);
        method.setAccessible(true);
        method.invoke(httpClient, arguments);
        method.setAccessible(false);
        Assert.assertEquals(httpClient.getResponseCode(), "200");
        Assert.assertEquals(httpClient.getResponseStatus(), "OK");
    }

    @Test
    public void testParseResponseStatus_chunked() throws UnsupportedEncodingException,
            NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        this.parseResponseStatus(chunkedInputStream);
    }

    @Test
    public void testParseResponseStatus_not_chunked() throws UnsupportedEncodingException,
            NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        this.parseResponseStatus(notChunkedInputStream);
    }

    @Test
    public void testParseResponseHeader_chunked() throws UnsupportedEncodingException,
            NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        this.parseResponseStatus(chunkedInputStream);
        Class[] parameterTypes = { InputStream.class };
        Object[] arguments = { chunkedInputStream };
        HttpClient httpClient = new HttpClient();
        Method method = httpClient.getClass().getDeclaredMethod("parseResponseHeader", parameterTypes);
        method.setAccessible(true);
        method.invoke(httpClient, arguments);
        method.setAccessible(false);
        Assert.assertEquals(httpClient.getResponseHeader("X-Powered-By"), "Servlet/3.1");
        Assert.assertEquals(httpClient.getResponseHeader("Content-Type"), "text/html; charset=utf-8");
        Assert.assertEquals(httpClient.getResponseHeader("Content-Language"), "zh-CN");
        Assert.assertEquals(httpClient.getResponseHeader("Transfer-Encoding"), "chunked");
        Assert.assertEquals(httpClient.getResponseHeader("Date"), "Wed, 08 Nov 2017 01:04:40 GMT");
    }

    @Test
    public void testParseResponseBodyChunked() throws InvocationTargetException,
            NoSuchMethodException, IllegalAccessException, UnsupportedEncodingException {
        this.testParseResponseHeader_chunked();
        Class[] parameterTypes = { InputStream.class };
        Object[] arguments = {chunkedInputStream};
        HttpClient httpClient = new HttpClient();
        Method method = httpClient.getClass().getDeclaredMethod("parseResponseBodyChunked", parameterTypes);
        method.setAccessible(true);
        byte[] bytes = (byte[])method.invoke(httpClient, arguments);
        method.setAccessible(false);
        Assert.assertEquals(new String(bytes, "UTF-8"), "{\"resultFlag\":\"0\",\"resultList\":[]," +
                "\"recordsTotal\":\"0\"}");
    }

    private HttpClient parseResponseHeader_not_chunked() throws UnsupportedEncodingException,
            NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        this.parseResponseStatus(notChunkedInputStream);
        Class[] parameterTypes = { InputStream.class };
        Object[] arguments = { notChunkedInputStream };
        HttpClient httpClient = new HttpClient();
        Method method = httpClient.getClass().getDeclaredMethod("parseResponseHeader", parameterTypes);
        method.setAccessible(true);
        method.invoke(httpClient, arguments);
        method.setAccessible(false);
        return httpClient;
    }

    @Test
    public void testParseResponseHeader_not_chunked() throws UnsupportedEncodingException,
            NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        HttpClient httpClient = this.parseResponseHeader_not_chunked();
        Assert.assertEquals(httpClient.getResponseHeader("X-Powered-By"), "Servlet/3.1");
        Assert.assertEquals(httpClient.getResponseHeader("Content-Type"), "text/html; charset=utf-8");
        Assert.assertEquals(httpClient.getResponseHeader("Content-Language"), "zh-CN");
        Assert.assertEquals(httpClient.getResponseHeader("Content-Length"), "53");
        Assert.assertEquals(httpClient.getResponseHeader("Date"), "Wed, 08 Nov 2017 01:04:40 GMT");
    }

    @Test
    public void testParseResponseBody() throws InvocationTargetException,
            NoSuchMethodException, IllegalAccessException, UnsupportedEncodingException {
        HttpClient httpClient = this.parseResponseHeader_not_chunked();
        Class[] parameterTypes = { InputStream.class };
        Object[] arguments = {notChunkedInputStream};
        Method method = httpClient.getClass().getDeclaredMethod("parseResponseBody", parameterTypes);
        method.setAccessible(true);
        byte[] bytes = (byte[])method.invoke(httpClient, arguments);
        method.setAccessible(false);
        Assert.assertEquals(new String(bytes, "UTF-8"), "{\"resultFlag\":\"0\",\"resultList\":[]," +
                "\"recordsTotal\":\"0\"}");
    }

    @Test
    public void testFetch_ip() throws Exception {
        HttpClient httpClient = null;
        try {
            httpClient = new HttpClient("122.26.13.158", 9080);
            httpClient.addRequestHeader("Content-Type", "application/json");
            httpClient.setMethod("GET");
            httpClient.setRequestBody("{\"appName\":\"F-GRAM\",\"curDate\":\"\",\"logType\":\"\"," +
                    "\"logName\":\"\",\"timeBegin\":\"\",\"timeEnd\":\"\",\"rcName\":\"\",\"esClusterId\":\"\"," +
                    "\"logInfo\":\"\",\"start\":\"\",\"length\":\"\"}");
            byte[] bytes = httpClient.fetch("/icbc/paas/api/log/searchlog");
            System.out.println(new String(bytes, "UTF-8"));
        } finally {
            if (httpClient != null) {
                httpClient.close();
            }
        }
    }

    @Test
    public void testFetch_domain() throws Exception {
        HttpClient httpClient = null;
        try {
            httpClient = new HttpClient("www.baidu.com", 80);
            httpClient.setMethod("GET");
            byte[] bytes = httpClient.fetch("/");
            System.out.println(new String(bytes, "UTF-8"));
        } finally {
            if (httpClient != null) {
                httpClient.close();
            }
        }
    }
}