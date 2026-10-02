package com.miniaturesoftwares.xpjobssuperviser.cybercap

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * Portrait-locked barcode scanner.
 *
 * zxing-android-embedded declares its own CaptureActivity as `sensorLandscape`
 * in the library manifest, so the scanner opens sideways no matter what
 * ScanOptions asks for. Subclassing it and re-declaring the subclass as
 * `screenOrientation="portrait"` in our manifest is the only way to override
 * that, since manifest merging cannot change an attribute on the library's own
 * activity entry.
 *
 * No behaviour is added - this exists purely to carry a different manifest
 * declaration.
 */
class PortraitCaptureActivity : CaptureActivity()
