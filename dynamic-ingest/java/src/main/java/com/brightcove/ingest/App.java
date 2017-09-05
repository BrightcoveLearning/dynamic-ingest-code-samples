package com.brightcove.ingest;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class App
{
	public static final String oAuthClientId = "REDACTED";
	public static final String oAuthClientSecret = "REDACTED";
	public static final String accountId = "ADD YOUR ACCOUNT ID HERE";

	public static final String accessTokenUrl = "https://oauth.brightcove.com/v4/access_token";
    public static final String createVideoUrl = "https://cms.api.brightcove.com/v1/accounts/ACCOUNT_ID/videos/";
	public static final String uploadUrlsUrl = "https://cms.api.brightcove.com/v1/accounts/ACCOUNT_ID/videos/VIDEO_ID/upload-urls/SOURCE_NAME";
    public static final String dynamicIngestUrl = "https://ingest.api.brightcove.com/v1/accounts/ACCOUNT_ID/videos/VIDEO_ID/ingest-requests";
    public static final String masterFileName = "265_ColoCribs.mp4";

    private static final Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();

	public static String getAccessToken() throws Exception {
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(oAuthClientId, oAuthClientSecret));

		// The use of an AuthCache and an HttpClient context is required to perform Preemptive authorization
		// as required by oauth.brightcove.com
		AuthCache authCache = new BasicAuthCache();
		authCache.put(new HttpHost("oauth.brightcove.com", 443, "https"), new BasicScheme());
		HttpClientContext context = HttpClientContext.create();
		context.setCredentialsProvider(credsProvider);
		context.setAuthCache(authCache);

		HttpPost request = new HttpPost(accessTokenUrl);

		ArrayList<NameValuePair> postParameters = new ArrayList<NameValuePair>();
		postParameters.add(new BasicNameValuePair("grant_type", "client_credentials"));
		request.setEntity(new UrlEncodedFormEntity(postParameters));

		CloseableHttpClient client = HttpClientBuilder.create().build();
		HttpResponse response = client.execute(request, context);

		System.out.println(response.getStatusLine());
		HttpEntity entity = response.getEntity();

		AccessTokenResponse atr = gson.fromJson(EntityUtils.toString(entity), AccessTokenResponse.class);
		return atr.getAccessToken();
	}

    // There should be a way to do this with anonymous types, rather than casts to object
    public static Object executeAuthorizedRequest(HttpUriRequest request, Object returnType) throws Exception {
        String accessToken = getAccessToken();
        request.setHeader("Authorization", "Bearer " + accessToken);

        CloseableHttpClient client = HttpClientBuilder.create().build();
        HttpResponse response = client.execute(request);
        HttpEntity entity = response.getEntity();
        String responseString = EntityUtils.toString(entity);

        System.err.println(responseString);
        return gson.fromJson(responseString, returnType.getClass());
    }

    public static CreateVideoResponse createVideo(String accountId) throws Exception {
        Map<String, String> videoData = new HashMap<String, String>();
        videoData.put("name", "my video");

        String url = createVideoUrl.replace("ACCOUNT_ID", accountId);
        HttpPost request = new HttpPost(url);
        request.setEntity(new StringEntity(gson.toJson(videoData)));

        return (CreateVideoResponse)executeAuthorizedRequest(request, new CreateVideoResponse());
    }

    public static UploadUrlsResponse getUploadUrl(String accountId, String videoId, String sourceName) throws Exception {
        String url = uploadUrlsUrl.replace("ACCOUNT_ID", accountId).
                replace("VIDEO_ID", videoId).replace("SOURCE_NAME", sourceName);
        HttpGet request = new HttpGet(url);
        return (UploadUrlsResponse)executeAuthorizedRequest(request, new UploadUrlsResponse());
    }

    public static void uploadFile(UploadUrlsResponse uploadLocation, File file) throws Exception {
        AWSCredentials credentials = new BasicSessionCredentials(uploadLocation.getAccessKeyId(), uploadLocation.getSecretAccessKey(), uploadLocation.getSessionToken());
        TransferManager transferManager = new TransferManager(credentials);
        Upload upload = transferManager.upload(uploadLocation.getBucket(), uploadLocation.getObjectKey(), file);
        upload.waitForUploadResult();
    }

    public static DynamicIngestResponse submitDynamicIngest(String accountId, String videoId, String masterUrl) throws Exception {
        Map<String, String> masterData = new HashMap<String, String>();
        masterData.put("url", masterUrl);

        Map<String, Object> requestData = new HashMap<String, Object>();
        requestData.put("master", masterData);

        String url = dynamicIngestUrl.replace("ACCOUNT_ID", accountId).replace("VIDEO_ID", videoId);
        HttpPost request = new HttpPost(url);
        request.setEntity(new StringEntity(gson.toJson(requestData)));

        return (DynamicIngestResponse)executeAuthorizedRequest(request, new DynamicIngestResponse());
    }


    public static void main( String[] args ) throws Exception {
        CreateVideoResponse video = createVideo(accountId);

        System.out.println("Requesting S3 Upload Location");
		UploadUrlsResponse uploadLocation = getUploadUrl(accountId, video.getId(), masterFileName);

        System.out.println("Uploading file to S3");
        File f = new File(App.class.getClassLoader().getResource(masterFileName).getFile());
        uploadFile(uploadLocation, f);

        System.out.println("Submitting Dynamic Ingest request");
        DynamicIngestResponse di = submitDynamicIngest(accountId, video.getId(), uploadLocation.getApiRequestUrl());
        System.out.println(di.getId());
    }
}
