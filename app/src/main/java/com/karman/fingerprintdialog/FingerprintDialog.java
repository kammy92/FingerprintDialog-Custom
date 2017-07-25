/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.karman.fingerprintdialog;

import android.annotation.SuppressLint;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

/**
 * A dialog which uses fingerprint APIs to authenticate the user, and falls back to password
 * authentication if fingerprint is not available.
 */
@SuppressLint("ValidFragment")
public class FingerprintDialog extends DialogFragment {
    String USE_FINGERPRINT_IN_FUTURE = "use_fingerprint_in_future";
    
    public static String SECRET_MESSAGE = "secret_message";
    String DEFAULT_KEY_NAME = "default_key_name";
    
    KeyStore keyStore;
    KeyGenerator keyGenerator;
    Cipher cipher;
    ImageView ivIcon;
    TextView tvMessage;
    Button btPositive, btNeutral, btNegative;
    CheckBox cbFingerprintInFuture;
    RelativeLayout rlFingerprint, rlPassword;
    EditText etPassword;
    TextView tv2;
    
    Context context;
    
    
    boolean isNewFingerprintEnrolled = false;
    
    
    AuthenticationType authenticationType = AuthenticationType.FINGERPRINT;
    
    private FingerprintManager.CryptoObject cryptoObject;
    private FingerprintUiHelper fingerprintUiHelper;
    private MainActivity mActivity;
    
    private InputMethodManager mInputMethodManager;
    private SharedPreferences mSharedPreferences;
    
    @Override
    public void onAttach (Context context) {
        super.onAttach (context);
        this.context = context;
        mActivity = (MainActivity) getActivity ();
        mInputMethodManager = context.getSystemService (InputMethodManager.class);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences (context);
        fingerprintUiHelper = new FingerprintUiHelper (context.getSystemService (FingerprintManager.class));
        checkFingerprintAvailable (context);
        initKeys (context);
    }
    
    @Override
    public void onCreate (Bundle savedInstanceState) {
        super.onCreate (savedInstanceState);
        setRetainInstance (true);
        setStyle (DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog);
    }
    
    @Override
    public View onCreateView (LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getDialog ().setTitle (getString (R.string.sign_in));
        View v = inflater.inflate (R.layout.fingerprint_dialog, container, false);
        
        initView (v);
        initListener ();
        
        if (! fingerprintUiHelper.isFingerprintAuthAvailable ()) {
            showPasswordLayout ();
        }
        return v;
    }
    
    public void initView (View v) {
        ivIcon = (ImageView) v.findViewById (R.id.ivIcon);
        tvMessage = (TextView) v.findViewById (R.id.tvMessage);
        btPositive = (Button) v.findViewById (R.id.btPositive);
        btNegative = (Button) v.findViewById (R.id.btNegative);
        btNeutral = (Button) v.findViewById (R.id.btNeutral);
        cbFingerprintInFuture = (CheckBox) v.findViewById (R.id.cbFingerprintInFuture);
        rlFingerprint = (RelativeLayout) v.findViewById (R.id.rlFingerprint);
        rlPassword = (RelativeLayout) v.findViewById (R.id.rlPassword);
        etPassword = (EditText) v.findViewById (R.id.etPassword);
        tv2 = (TextView) v.findViewById (R.id.tv2);
    }
    
    public void initListener () {
        btNegative.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View view) {
                dismiss ();
            }
        });
        btNeutral.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View view) {
                if (authenticationType == AuthenticationType.FINGERPRINT) {
                    showPasswordLayout ();
                } else {
                    showFingerprintLayout ();
                }
            }
        });
        etPassword.addTextChangedListener (new TextWatcher () {
            @Override
            public void onTextChanged (CharSequence s, int start, int before, int count) {
                if (s.toString ().length () > 0) {
                    btPositive.setEnabled (true);
                } else {
                    btPositive.setEnabled (false);
                }
            }
            
            @Override
            public void beforeTextChanged (CharSequence s, int start, int count, int after) {
            }
            
            @Override
            public void afterTextChanged (Editable s) {
            }
        });
        
        etPassword.setOnEditorActionListener (new TextView.OnEditorActionListener () {
            @Override
            public boolean onEditorAction (TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    verifyPassword ();
                    return true;
                }
                return false;
            }
        });
        
        btPositive.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View view) {
                verifyPassword ();
            }
        });
    }
    
    
    @Override
    public void onResume () {
        super.onResume ();
        if (authenticationType == AuthenticationType.FINGERPRINT) {
            fingerprintUiHelper.startListening (cryptoObject);
        }
    }
    
    @Override
    public void onPause () {
        super.onPause ();
        fingerprintUiHelper.stopListening ();
    }
    
    private void showPasswordLayout () {
        btNeutral.setText ("FINGERPRINT");
        tv2.setText ("Enter password to continue");
        rlFingerprint.setVisibility (View.GONE);
        rlPassword.setVisibility (View.VISIBLE);
        
        if (isNewFingerprintEnrolled) {
            authenticationType = AuthenticationType.NEW_FINGERPRINT_ENROLLED;
            btNeutral.setEnabled (false);
            tv2.setText ("New fingerprint enrolled, password compulsory");
            cbFingerprintInFuture.setVisibility (View.VISIBLE);
        } else {
            authenticationType = AuthenticationType.PASSWORD;
        }
        etPassword.requestFocus ();
        etPassword.postDelayed (mShowKeyboardRunnable, 100);
        fingerprintUiHelper.stopListening ();
    }
    
    private void showFingerprintLayout () {
        hideKeyboard ();
        authenticationType = AuthenticationType.FINGERPRINT;
        fingerprintUiHelper.startListening (cryptoObject);
        btPositive.setEnabled (false);
        btNeutral.setText ("PASSWORD");
        etPassword.setText ("");
        rlFingerprint.setVisibility (View.VISIBLE);
        rlPassword.setVisibility (View.GONE);
    }
    
    private void verifyPassword () {
        if (! checkPassword (etPassword.getText ().toString ())) {
            etPassword.setError ("Password Not Match");
            return;
        }
        if (authenticationType == AuthenticationType.NEW_FINGERPRINT_ENROLLED) {
            SharedPreferences.Editor editor = mSharedPreferences.edit ();
            editor.putBoolean (USE_FINGERPRINT_IN_FUTURE, cbFingerprintInFuture.isChecked ());
            editor.apply ();
            
            if (cbFingerprintInFuture.isChecked ()) {
                createKey (DEFAULT_KEY_NAME);
                authenticationType = AuthenticationType.FINGERPRINT;
            }
        }
        etPassword.setText ("");
        mActivity.onSuccessfulAuthentication (false /* without Fingerprint */, null);
        dismiss ();
    }
    
    
    public void createKey (String keyName) {
        try {
            keyStore.load (null);
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec
                    .Builder (keyName, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes (KeyProperties.BLOCK_MODE_CBC)
                    .setUserAuthenticationRequired (true)
                    .setEncryptionPaddings (KeyProperties.ENCRYPTION_PADDING_PKCS7);
            keyGenerator.init (builder.build ());
            keyGenerator.generateKey ();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException
                | CertificateException | IOException e) {
            throw new RuntimeException (e);
        }
    }
    
    public void initKeys (Context context) {
        try {
            keyStore = KeyStore.getInstance ("AndroidKeyStore");
        } catch (KeyStoreException e) {
            throw new RuntimeException ("Failed to get an instance of KeyStore", e);
        }
        try {
            keyGenerator = KeyGenerator.getInstance (KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            throw new RuntimeException ("Failed to get an instance of KeyGenerator", e);
        }
        try {
            cipher = Cipher.getInstance (
                    KeyProperties.KEY_ALGORITHM_AES + "/"
                            + KeyProperties.BLOCK_MODE_CBC + "/"
                            + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException ("Failed to get an instance of Cipher", e);
        }
        createKey (DEFAULT_KEY_NAME);
        
        try {
            keyStore.load (null);
            SecretKey key = (SecretKey) keyStore.getKey (DEFAULT_KEY_NAME, null);
            cipher.init (Cipher.ENCRYPT_MODE, key);
            cryptoObject = new FingerprintManager.CryptoObject (cipher);
            mSharedPreferences = PreferenceManager.getDefaultSharedPreferences (context);
            if (mSharedPreferences.getBoolean (USE_FINGERPRINT_IN_FUTURE, true)) {
                authenticationType = AuthenticationType.FINGERPRINT;
            } else {
                authenticationType = AuthenticationType.PASSWORD;
            }
            isNewFingerprintEnrolled = false;
        } catch (KeyPermanentlyInvalidatedException e) {
            authenticationType = AuthenticationType.NEW_FINGERPRINT_ENROLLED;
            isNewFingerprintEnrolled = true;
        } catch (KeyStoreException | CertificateException | UnrecoverableKeyException | IOException
                | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException ("Failed to initKeys Cipher", e);
        }
    }
    
    public boolean checkFingerprintAvailable (Context context) {
        KeyguardManager keyguardManager = context.getSystemService (KeyguardManager.class);
        FingerprintManager fingerprintManager = context.getSystemService (FingerprintManager.class);
        
        if (! keyguardManager.isKeyguardSecure ()) {
            // Show a message that the user hasn't set up a fingerprint or lock screen.
            Toast.makeText (context,
                    "Secure lock screen hasn't set up.\n"
                            + "Go to 'Settings -> Security -> Fingerprint' to set up a fingerprint",
                    Toast.LENGTH_LONG).show ();
            return false;
        }
        // The line below prevents the false positive inspection from Android Studio
        // noinspection ResourceType
        if (! fingerprintManager.hasEnrolledFingerprints ()) {
            // This happens when no fingerprints are registered.
            Toast.makeText (context,
                    "Go to 'Settings -> Security -> Fingerprint' and register at least one fingerprint",
                    Toast.LENGTH_LONG).show ();
            return false;
        }
        return true;
    }
    
    public void showFingerprintDialog (FragmentManager fragmentManager) {
        show (fragmentManager, null);
    }
    
    
    private final Runnable mShowKeyboardRunnable = new Runnable () {
        @Override
        public void run () {
            mInputMethodManager.showSoftInput (etPassword, 0);
        }
    };
    
    private enum AuthenticationType {
        FINGERPRINT,
        NEW_FINGERPRINT_ENROLLED,
        PASSWORD
    }
    
    private void hideKeyboard () {
        new Handler ().postDelayed (new Runnable () {
            @Override
            public void run () {
                try {
                    InputMethodManager inputManager = (InputMethodManager) context.getSystemService (Context.INPUT_METHOD_SERVICE);
                    inputManager.hideSoftInputFromWindow (getView ().getWindowToken (), 0);
                } catch (Exception e) {
                }
            }
        }, 200);
        
    }
    
    public class FingerprintUiHelper extends FingerprintManager.AuthenticationCallback {
        
        private static final long ERROR_TIMEOUT_MILLIS = 1500;
        private static final long SUCCESS_DELAY_MILLIS = 1000;
        
        private final FingerprintManager mFingerprintManager;
        private CancellationSignal mCancellationSignal;
        
        private boolean mSelfCancelled;
        private Runnable mResetErrorTextRunnable = new Runnable () {
            @Override
            public void run () {
                tvMessage.setTextColor (getResources ().getColor (R.color.hint_color, null));
                tvMessage.setText (getResources ().getString (R.string.fingerprint_hint));
                ivIcon.setImageResource (R.drawable.ic_fp_40px);
            }
        };
        
        FingerprintUiHelper (FingerprintManager fingerprintManager) {
            mFingerprintManager = fingerprintManager;
        }
        
        public boolean isFingerprintAuthAvailable () {
            // The line below prevents the false positive inspection from Android Studio
            // noinspection ResourceType
            return mFingerprintManager.isHardwareDetected () && mFingerprintManager.hasEnrolledFingerprints ();
        }
        
        public void startListening (FingerprintManager.CryptoObject cryptoObject) {
            if (! isFingerprintAuthAvailable ()) {
                return;
            }
            mCancellationSignal = new CancellationSignal ();
            mSelfCancelled = false;
            // The line below prevents the false positive inspection from Android Studio
            // noinspection ResourceType
            mFingerprintManager.authenticate (cryptoObject, mCancellationSignal, 0 /* flags */, this, null);
            ivIcon.setImageResource (R.drawable.ic_fp_40px);
        }
        
        public void stopListening () {
            if (mCancellationSignal != null) {
                mSelfCancelled = true;
                mCancellationSignal.cancel ();
                mCancellationSignal = null;
            }
        }
        
        @Override
        public void onAuthenticationError (int errMsgId, CharSequence errString) {
            if (! mSelfCancelled) {
                showError (errString);
                ivIcon.postDelayed (new Runnable () {
                    @Override
                    public void run () {
                        onError ();
                    }
                }, ERROR_TIMEOUT_MILLIS);
            }
        }
        
        @Override
        public void onAuthenticationHelp (int helpMsgId, CharSequence helpString) {
            showError (helpString);
        }
        
        @Override
        public void onAuthenticationFailed () {
            showError (getResources ().getString (R.string.fingerprint_not_recognized));
        }
        
        @Override
        public void onAuthenticationSucceeded (FingerprintManager.AuthenticationResult result) {
            tvMessage.removeCallbacks (mResetErrorTextRunnable);
            ivIcon.setImageResource (R.drawable.ic_fingerprint_success);
            tvMessage.setTextColor (getResources ().getColor (R.color.success_color, null));
            tvMessage.setText (getResources ().getString (R.string.fingerprint_success));
            ivIcon.postDelayed (new Runnable () {
                @Override
                public void run () {
                    onAuthenticated ();
                }
            }, SUCCESS_DELAY_MILLIS);
        }
        
        private void showError (CharSequence error) {
            ivIcon.setImageResource (R.drawable.ic_fingerprint_error);
            tvMessage.setText (error);
            tvMessage.setTextColor (getResources ().getColor (R.color.warning_color, null));
            tvMessage.removeCallbacks (mResetErrorTextRunnable);
            tvMessage.postDelayed (mResetErrorTextRunnable, ERROR_TIMEOUT_MILLIS);
        }
        
        void onAuthenticated () {
            mActivity.onSuccessfulAuthentication (true /* withFingerprint */, cryptoObject);
            dismiss ();
        }
        
        void onError () {
            showPasswordLayout ();
        }
    }
    
    private boolean checkPassword (String password) {
        if (password.equalsIgnoreCase ("123456")) {
            return true;
        } else {
            return false;
        }
    }
}