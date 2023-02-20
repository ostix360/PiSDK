package fr.ostix;

import org.junit.jupiter.api.*;

import java.io.*;
import java.util.*;


public class MainTest {

    @Test
    public void testLoadAccount() {
        String apiKey = "dfljlnxzl2luo7suwqwfgatcmzvkj2miwj3crrjwyku88lnrtx33cmftnbtdimra";
        String wallet = "SBNGKCS7C25WO6D4KSUOIN2UTIZWMOYSDCAGRU3UAPOZ5MCTA5O4K323";
        String networkId = "Pi Testnet";
        PiNetwork piNetwork = new PiNetwork(apiKey, wallet, networkId);
        System.out.println(piNetwork.getBalanceFromPublicKey("GCOULCLALBWBXGG4WX5X2TA2HGWULLBL3KUHHB7B5V3CGR7OGNH5YB4B"));
        try {
            piNetwork.getPayment("48291422570446849");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        HashMap<String, String> paymentData = new HashMap<>();
        paymentData.put("memo", "test");
        paymentData.put("metadata", "{}");
        paymentData.put("uid", "GET-THIS-SECRET-DATA-FROMFRONTEND");
        paymentData.put("amount", "1");
        try {
            System.out.println(piNetwork.createPayment(paymentData));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
