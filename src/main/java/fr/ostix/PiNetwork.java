package fr.ostix;

import okhttp3.*;
import okhttp3.Response;
import org.stellar.sdk.*;
import org.stellar.sdk.responses.*;
import shadow.com.google.gson.*;

import java.io.*;
import java.net.*;
import java.util.*;

public class PiNetwork {


    private final String apiKey;
    private final OkHttpClient client;
    private AccountResponse account;
    private final String baseUrl;
    private String fromAddress;
    private HashMap<String, JsonObject> paymentData;
    private final Network network;
    private Server server;
    private KeyPair pair;
    private final long baseFee;

    private String host;


    public PiNetwork(String apiKey, String walletPrivateSeed, String networkId) {
        if (!validatePrivateSeedFormat(walletPrivateSeed)) {
            System.err.println("Invalid private seed format, please check your wallet private seed.");
            System.exit(1);
        }
        this.baseUrl = "https://api.minepi.com";
        this.network = new Network(networkId);
        this.apiKey = apiKey;
        this.loadAccount(walletPrivateSeed, networkId);
        try {
            this.baseFee = server.feeStats().execute().getLastLedgerBaseFee();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        client = new OkHttpClient();
    }

    public float getBalance() {
        try {
            for (AccountResponse.Balance balance : account.getBalances()) {
                if (balance.getAssetType().equals("native")) {
                    return Float.parseFloat(balance.getBalance());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public float getBalanceFromPublicKey(String key) {
        try {
            AccountResponse account = server.accounts().account(key);
            for (AccountResponse.Balance balance : account.getBalances()) {
                if (balance.getAssetType().equals("native")) {
                    return Float.parseFloat(balance.getBalance());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public String getPayment(String paymentId) throws IOException {
        String url = baseUrl + "/v2/payments/" + paymentId;
        Request request = new Request.Builder()
                .url(url)
                .headers(okhttp3.Headers.of(getHttpHeaders()))
                .build();
        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            return response.body().string();
        }
    }

    public String createPayment(HashMap<String, String> paymentData) throws IOException {
        if (!validatePaymentData(paymentData)) {
            System.err.println("Invalid payment data, please check your payment data.");
        }
        for (AccountResponse.Balance balance : account.getBalances()) { //TODO why
            if (balance.getAssetType().equals("native")) {
                if (Float.parseFloat(paymentData.get("amount")) + baseFee / 10000000f > Float.parseFloat(balance.getBalance())) {
                    return "";
                }
                break;
            }
        }
        URL url = new URL(this.baseUrl + "/v2/payments");
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        Gson gson = new Gson();
        String json = gson.toJson(paymentData);
        RequestBody body = RequestBody.create("{ \"payment\":" + json + "}", JSON);
        Response re = sendPostRequest(url, body);
        if (re.isSuccessful()) {
            assert re.body() != null;
            gson = new Gson();
            JsonObject jsonObject = gson.fromJson(re.body().string(), JsonObject.class);


            String id = jsonObject.get("identifier").getAsString();
            this.paymentData.put(id, jsonObject);
            return id;
        } else {
            System.err.println(re.message());
            System.err.println("Error while creating payment, please check your payment data.");
        }
        return "";
    }

    public String submitPayment(String paymentId, JsonObject pendingPayment) throws IOException { //TODO test with Pi sandbox
        if (paymentData.containsKey(paymentId)) {
            JsonObject payment;
            if (pendingPayment != null) {
                payment = pendingPayment;
            } else {
                payment = paymentData.get(paymentId);
            }
            for (AccountResponse.Balance balance : account.getBalances()) { //TODO why
                if (balance.getAssetType().equals("native")) {
                    if (Float.parseFloat(payment.get("amount").getAsString()) + baseFee / 10000000f > Float.parseFloat(balance.getBalance())) {
                        return "";
                    }
                    break;
                }
            }
            System.out.println("payment: " + payment);
            this.setHorizonClient(payment.get("network").getAsString());
            String from = payment.get("from_address").getAsString();

            JsonObject transactionData = new JsonObject();
            transactionData.add("amount", payment.get("amount"));
            transactionData.add("identifier", payment.get("identifier"));
            transactionData.add("recipient", payment.get("to_address"));

            Transaction transaction = this.buildA2UTransaction(payment);
            String txid = this.submitTransaction(transaction);
            paymentData.remove(paymentId);
            return txid;
        } else {
            System.err.println("Invalid payment id, please check your payment id.");
        }
        return "";
    }

    public void completePayment(String identifier, String txid) throws IOException {
        URL url = new URL(this.baseUrl + "/v2/payments/" + identifier + "/complete");
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create("{ \"txid\":" + txid + "}", JSON);
        Response re = sendPostRequest(url, body);
        if (re.isSuccessful()) {
            assert re.body() != null;
            System.out.println(re.body().string());
        } else {
            System.err.println(re.message());
            System.err.println("Error while completing payment, please check your payment data.");
        }
    }

    public void cancelPayment(String identifier) throws IOException {
        URL url = new URL(this.baseUrl + "/v2/payments/" + identifier + "/cancel");
        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create("", JSON);
        Response re = sendPostRequest(url, body);
        if (re.isSuccessful()) {
            assert re.body() != null;
            System.out.println(re.body().string());
        } else {
            System.err.println(re.message());
            System.err.println("Error while cancelling payment, please check your payment data.");
        }
    }

    public String getIncompletePayments() throws IOException {
        String url = baseUrl + "/v2/payments/incomplete_server_payments";
        Request request = new Request.Builder()
                .url(url)
                .headers(okhttp3.Headers.of(getHttpHeaders()))
                .build();
        try (Response re = client.newCall(request).execute()) {
            assert re.body() != null;
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(re.body().string(), JsonObject.class);
            return jsonObject.get("incomplete_server_payments").getAsString();
        }
    }

    private void setHorizonClient(String network) { //TODO why
//        if (network.equals("Pi Network")) {
//            this.host = "api.mainnet.minepi.com";
//        } else if (network.equals("Pi Testnet")) {
//            this.host = "api.testnet.minepi.com";
//        } else {
//            System.err.println("Invalid network, please check your network.");
//        }
//        server = new Server("https://" + host);
    }

    private Response sendPostRequest(URL url, RequestBody body) throws IOException {

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Key " + apiKey)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response;
        }
    }


    private void loadAccount(String walletPrivateSeed, String networkId) {
        this.pair = KeyPair.fromSecretSeed(walletPrivateSeed);
        String horizon;
        if (networkId.equals("Pi Network")) {
            this.host = "api.mainnet.minepi.com";
            horizon = "https://api.mainnet.minepi.com";
        } else if (networkId.equals("Pi Testnet")) {
            this.host = "api.testnet.minepi.com";
            horizon = "https://api.testnet.minepi.com";
        } else {
            System.err.println("Invalid network id, Pi Testnet is used by default.");
            this.host = "api.testnet.minepi.com";
            horizon = "https://api.testnet.minepi.com";
        }
        try {
            this.server = new Server(horizon);
            this.account = server.accounts().account(pair.getAccountId());
            System.out.println("Balances for account " + pair.getAccountId());
            System.out.println("Native balance: " + account.getBalances()[0].getBalance());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Transaction buildA2UTransaction(JsonObject transactionData) {
        if (this.validatePaymentData(transactionData)){
            try {
                String amount = transactionData.get("amount").getAsString();
                String toAddress = transactionData.get("to_address").getAsString();
                String memo = transactionData.get("identifier").getAsString();
                System.out.println("MEMO: " + memo);
                return new TransactionBuilder(account, network)
                        .addOperation(new PaymentOperation.Builder(toAddress, new AssetTypeNative(), amount).build())
                        // A memo allows you to add your own metadata to a transaction. It's
                        // optional and does not affect how Stellar treats the transaction.
                        .addMemo(Memo.text(memo))
                        // Wait a maximum of three minutes for the transaction
                        .setTimeout(30)
                        // Set the amount of lumens you're willing to pay per operation to submit your transaction
                        .setBaseFee((int) baseFee)
                        .build();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private String submitTransaction(Transaction transaction) {
        try {
            transaction.sign(pair);
            SubmitTransactionResponse response = server.submitTransaction(transaction);
            System.out.println("Success!");
            System.out.println(response.getHash());
            System.out.println(response);
            return response.getHash();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }


    private Map<String, String> getHttpHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Key " + apiKey);
        return headers;
    }

    private boolean validatePaymentData(HashMap<String, String> paymentData) {
        if (paymentData.containsKey("amount") &&
                paymentData.containsKey("memo") &&
                paymentData.containsKey("metadata") &&
                paymentData.containsKey("uid")) {
            return true;
        } else {
            System.err.println("Invalid payment data, please check your payment data.");
            return false;
        }
    }

    private boolean validatePaymentData(JsonObject paymentData) {
        if (paymentData.has("amount") &&
                paymentData.has("memo") &&
                paymentData.has("metadata") &&
                paymentData.has("uid") &&
            paymentData.has("identifier") &&
                paymentData.has("recipient")) {
        return true;
        } else{
            System.err.println("Invalid payment data, please check your payment data.");
            return false;
        }
    }

    private boolean validatePrivateSeedFormat(String seed) {
        if (!seed.toUpperCase().startsWith("S")) {
            return false;
        } else return seed.length() == 56;
    }

    //    private String sendGetRequest(URL url) {
//        ClassicHttpRequest httpGet = ClassicRequestBuilder.get(url.toString())
//                .addHeader("Content-Type", "application/json")
//                .addHeader("Authorization", "Key " + apiKey)
//                .build();
//        return handleHttpResponse(httpGet);
//    }
}