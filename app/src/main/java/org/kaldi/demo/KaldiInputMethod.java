// Copyright 2019 Alpha Cephei Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.kaldi.demo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.inputmethodservice.InputMethodService;
import android.os.AsyncTask;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONObject;
import org.kaldi.Assets;
import org.kaldi.KaldiRecognizer;
import org.kaldi.Model;
import org.kaldi.RecognitionListener;
import org.kaldi.SpeechService;
import org.kaldi.Vosk;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;

import androidx.core.content.ContextCompat;

public class KaldiInputMethod extends InputMethodService implements
        RecognitionListener {

    static private final int STATE_START = 0;
    static private final int STATE_READY = 1;
    static private final int STATE_DONE = 2;
    static private final int STATE_FILE = 3;
    static private final int STATE_MIC = 4;


    private Model model;
    private SpeechService speechService;
    TextView resultView;

    private String textAccumulator = "";

    private View overlayView;

    @Override
    public void onInitializeInterface() {
        // called before UI elements created
        // a) after service first created
        // b) after config change happens

        // Check if user has given permission to record audio
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            // TODO: error => has to open settings first
            return;
        }
        // Recognizer initialization is a time-consuming and it involves IO,
        // so we execute it in async task
        new SetupTask(this).execute();
    }

    @Override
    public void onBindInput() {
        // when user first clicks e.g. in text field
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        // text input has started
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        // text input has ended
    }

    @Override
    public View onCreateInputView() {
        // create view
        overlayView = (View) getLayoutInflater().inflate(R.layout.main, null);

        // Setup layout
        resultView = overlayView.findViewById(R.id.result_text);
        setUiState(STATE_START);

//        overlayView.findViewById(R.id.recognize_file).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                recognizeFile();
//            }
//        });

        overlayView.findViewById(R.id.recognize_mic).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recognizeMicrophone();
            }
        });
        overlayView.findViewById(R.id.world).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // world
            }
        });
        overlayView.findViewById(R.id.backspace).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // delete last char (long press?)
                InputConnection ic = getCurrentInputConnection();
                if (ic != null)
                    ic.deleteSurroundingText(1, 0);
            }
        });
        overlayView.findViewById(R.id.colon).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendToComposing(":");
            }
        });
        overlayView.findViewById(R.id.exclamation).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendToComposing("!");
            }
        });
        overlayView.findViewById(R.id.question).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendToComposing("?");
            }
        });
        overlayView.findViewById(R.id.comma).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendToComposing(",");
            }
        });
        overlayView.findViewById(R.id.dot).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendToComposing(".");
            }
        });
        overlayView.findViewById(R.id.enter).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                appendToComposing("\n");
            }
        });

        return overlayView;
    }

    private void appendToComposing(String text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null)
            ic.commitText(text, 1);
    }

    private static class SetupTask extends AsyncTask<Void, Void, Exception> {
        WeakReference<KaldiInputMethod> activityReference;

        SetupTask(KaldiInputMethod activity) {
            this.activityReference = new WeakReference<>(activity);
        }

        @Override
        protected Exception doInBackground(Void... params) {
            try {
                Assets assets = new Assets(activityReference.get());
                File assetDir = assets.syncAssets();
                Log.d("KaldiDemo", "Sync files in the folder " + assetDir.toString());

                Vosk.SetLogLevel(0);

                //activityReference.get().model = new Model(assetDir.toString() + "/model-android");
                activityReference.get().model = new Model(assetDir.toString() + "/vosk-model-small-de-0.15");
            } catch (IOException e) {
                return e;
            }
            return null;
        }

        @Override
        protected void onPostExecute(Exception result) {
            if (result != null) {
                activityReference.get().setErrorState(String.format(activityReference.get().getString(R.string.failed), result));
            } else {
                activityReference.get().setUiState(STATE_READY);
            }
        }
    }

//    private static class RecognizeTask extends AsyncTask<Void, Void, String> {
//        WeakReference<KaldiInputMethod> activityReference;
//        WeakReference<TextView> resultView;
//
//        RecognizeTask(KaldiInputMethod activity, TextView resultView) {
//            this.activityReference = new WeakReference<>(activity);
//            this.resultView = new WeakReference<>(resultView);
//        }
//
//        @Override
//        protected String doInBackground(Void... params) {
//            KaldiRecognizer rec;
//            long startTime = System.currentTimeMillis();
//            StringBuilder result = new StringBuilder();
//            try {
//                rec = new KaldiRecognizer(activityReference.get().model, 16000.f, "[\"oh zero one two three four five six seven eight nine\"]");
//
//                InputStream ais = activityReference.get().getAssets().open("10001-90210-01803.wav");
//                if (ais.skip(44) != 44) {
//                    return "";
//                }
//                byte[] b = new byte[4096];
//                int nbytes;
//                while ((nbytes = ais.read(b)) >= 0) {
//                    if (rec.AcceptWaveform(b, nbytes)) {
//                        result.append(rec.Result());
//                    } else {
//                        result.append(rec.PartialResult());
//                    }
//                }
//                result.append(rec.FinalResult());
//            } catch (IOException e) {
//                return "";
//            }
//            return String.format(activityReference.get().getString(R.string.elapsed), result.toString(), (System.currentTimeMillis() - startTime));
//        }
//
//        @Override
//        protected void onPostExecute(String result) {
//            activityReference.get().setUiState(STATE_READY);
//            resultView.get().append(result + "\n");
//        }
//    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        if (speechService != null) {
            speechService.cancel();
            speechService.shutdown();
        }
    }


    @Override
    public void onResult(String hypothesis) {
        System.out.println(hypothesis);
        try {
            JSONObject result = new JSONObject(hypothesis);
            String text = result.getString("text");
            String finalText = text;
            switch (text) {
                case "punkt":
                    finalText = ".";
                    break;
            }
//            resultView.setText(finalText);
            InputConnection ic = getCurrentInputConnection();
            if (ic != null)
                ic.commitText(finalText, 1);
        } catch (Exception e) {
            System.out.println("ERROR: Json parse exception");
        }

    }

    @Override
    public void onPartialResult(String hypothesis) {
        System.out.println(hypothesis);
        try {
            JSONObject partialResult = new JSONObject(hypothesis);
            String partialText = partialResult.getString("partial");
//            resultView.setText(partialText);
            InputConnection ic = getCurrentInputConnection();
            ic.setComposingText(partialText, 1);
        } catch (Exception e) {
            System.out.println("ERROR: Json parse exception");
        }
    }

    @Override
    public void onError(Exception e) {
        setErrorState(e.getMessage());
    }

    @Override
    public void onTimeout() {
        speechService.cancel();
        speechService = null;
        setUiState(STATE_READY);
    }

    private void setUiState(int state) {
        switch (state) {
            case STATE_START:
                resultView.setText(R.string.preparing);
                resultView.setMovementMethod(new ScrollingMovementMethod());
//                overlayView.findViewById(R.id.recognize_file).setEnabled(false);
                overlayView.findViewById(R.id.recognize_mic).setEnabled(false);
                break;
            case STATE_READY:
                resultView.setText(R.string.ready);
                ((Button) overlayView.findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
//                overlayView.findViewById(R.id.recognize_file).setEnabled(true);
                overlayView.findViewById(R.id.recognize_mic).setEnabled(true);
                break;
            case STATE_DONE:
                ((Button) overlayView.findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
//                overlayView.findViewById(R.id.recognize_file).setEnabled(true);
                overlayView.findViewById(R.id.recognize_mic).setEnabled(true);
                break;
            case STATE_FILE:
                resultView.setText(getString(R.string.starting));
                overlayView.findViewById(R.id.recognize_mic).setEnabled(false);
//                overlayView.findViewById(R.id.recognize_file).setEnabled(false);
                break;
            case STATE_MIC:
                ((Button) overlayView.findViewById(R.id.recognize_mic)).setText(R.string.stop_microphone);
                resultView.setText(getString(R.string.say_something));
//                overlayView.findViewById(R.id.recognize_file).setEnabled(false);
                overlayView.findViewById(R.id.recognize_mic).setEnabled(true);
                break;
        }
    }

    private void setErrorState(String message) {
        resultView.setText(message);
        ((Button) overlayView.findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
//        overlayView.findViewById(R.id.recognize_file).setEnabled(false);
        overlayView.findViewById(R.id.recognize_mic).setEnabled(false);
    }

//    public void recognizeFile() {
//        setUiState(STATE_FILE);
//        new RecognizeTask(this, resultView).execute();
//    }

    public void recognizeMicrophone() {
        if (speechService != null) {
            setUiState(STATE_DONE);
            speechService.cancel();
            speechService = null;
        } else {
            setUiState(STATE_MIC);
            try {
                KaldiRecognizer rec = new KaldiRecognizer(model, 16000.0f);
                speechService = new SpeechService(rec, 16000.0f);
                speechService.addListener(this);
                speechService.startListening();
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

}
