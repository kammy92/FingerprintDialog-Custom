package com.karman.fingerprintdialog;

import android.app.Activity;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;


public class MainActivity extends Activity {
    
    Button purchaseButton;
    FingerprintDialog fingerprintDialog;
    
    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate (savedInstanceState);
        setContentView (R.layout.activity_main);
        initView ();
        initData ();
        initListener ();
    }
    
    private void initView () {
        purchaseButton = (Button) findViewById (R.id.purchase_button);
    }
    
    private void initData () {
        fingerprintDialog = new FingerprintDialog ();
    }
    
    private void initListener () {
        purchaseButton.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View v) {
                findViewById (R.id.confirmation_message).setVisibility (View.GONE);
                findViewById (R.id.encrypted_message).setVisibility (View.GONE);
                fingerprintDialog.showFingerprintDialog (getFragmentManager ());
            }
        });
    }
    
    
    public void onSuccessfulAuthentication (boolean withFingerprint, @Nullable FingerprintManager.CryptoObject cryptoObject) {
        if (withFingerprint) {
            // If the user has authenticated with fingerprint, verify that using cryptography and then show the confirmation message.
            assert cryptoObject != null;
            try {
                byte[] encrypted = cryptoObject.getCipher ().doFinal (FingerprintDialog.SECRET_MESSAGE.getBytes ());
                findViewById (R.id.confirmation_message).setVisibility (View.VISIBLE);
                if (encrypted != null) {
                    TextView v = (TextView) findViewById (R.id.encrypted_message);
                    v.setVisibility (View.VISIBLE);
                    v.setText (Base64.encodeToString (encrypted, 0 /* flags */));
                }
            } catch (BadPaddingException | IllegalBlockSizeException e) {
                Toast.makeText (this, "Failed to encrypt the data with the generated key. "
                        + "Retry the purchase", Toast.LENGTH_LONG).show ();
                Log.e ("TAG", "Failed to encrypt the data with the generated key." + e.getMessage ());
            } catch (Exception e) {
                Toast.makeText (this, "exception occured", Toast.LENGTH_SHORT).show ();
                e.printStackTrace ();
            }
        } else {
            findViewById (R.id.confirmation_message).setVisibility (View.VISIBLE);
            
            // Authentication happened with backup password. Just show the confirmation message.
        }
    }
}