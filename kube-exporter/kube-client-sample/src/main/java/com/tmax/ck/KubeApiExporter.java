package com.tmax.ck;

import java.util.concurrent.LinkedBlockingDeque;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import io.kubernetes.client.openapi.ApiClient;
import okhttp3.Call;
import okhttp3.Response;

public class KubeApiExporter extends Thread {
    private String url;
    private ApiClient client;
    private LinkedBlockingDeque<DataObject> insertQueue;
    private Gson gson = new Gson();
    
    public KubeApiExporter() {
        super();
    }

    public KubeApiExporter(String url, ApiClient client, LinkedBlockingDeque<DataObject> insertQueue) {
        this.url = url;
        this.client = client;
        this.insertQueue = insertQueue;
    }

    @Override
    public void run() {
        try {
            System.out.println("start sync : " + url);

            String line = null;
            Call call = GenericCaller.makeCall(url, client, true);
            Response response = call.execute();
            
            while(true) {
                /** connect or reconnect */
                if (call.isCanceled()) {
                    call = GenericCaller.makeCall(url, client, true);
                    response = call.execute();
                }

                /** read chunked response */
                line = response.body().source().readUtf8Line();

                /** enqueue DataObject */
                if (line != null) {
                    JsonObject jsonObject = gson.fromJson(line, JsonObject.class);
                    DataObject insertObject = null;
                    String action = getAction(jsonObject);
                    String key = getPK(jsonObject);
                    String payload = getPayload(jsonObject);
                    insertObject = new DataObject(action, key, payload);
                    insertQueue.push(insertObject);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getPayload(JsonObject jsonObject) {
        return jsonObject.get("object").toString();
    }

    private String getPK(JsonObject jsonObject) {
        String kind = jsonObject.get("object").getAsJsonObject().get("kind").getAsString();
        String namespace = jsonObject.get("object").getAsJsonObject().get("metadata").getAsJsonObject().get("namespace").getAsString();
        String name = jsonObject.get("object").getAsJsonObject().get("metadata").getAsJsonObject().get("name").getAsString();
        return kind + "." + namespace + "." + name;
    }

    private String getAction(JsonObject jsonObject) {
        return jsonObject.get("type").getAsString();
    }
}