Tesseract OCR traineddata files
==============================

Place language traineddata files here.  The app copies them to internal
storage on first launch so Tesseract can read them at runtime.

Download the "fast" (speed-optimised) models from:
  https://github.com/tesseract-ocr/tessdata_fast/raw/main/

Common Indian-language files:

  Hindi        hin.traineddata   (~1 MB)
  Tamil        tam.traineddata   (~3 MB)
  Telugu       tel.traineddata   (~3 MB)
  Bengali      ben.traineddata   (~1 MB)
  Kannada      kan.traineddata   (~3 MB)
  Malayalam    mal.traineddata   (~5 MB)
  Gujarati     guj.traineddata   (~1 MB)
  Marathi      mar.traineddata   (~2 MB)
  Punjabi      pan.traineddata   (~0.5 MB)
  English      eng.traineddata   (~4 MB)   -- optional; ML Kit covers Latin

At minimum, include hin.traineddata to enable Hindi OCR as a fallback
when ML Kit's Devanagari recogniser doesn't produce results.

The app will work without any files here — Tesseract simply won't be used
and OCR will fall back to ML Kit (Latin + Devanagari) only.
