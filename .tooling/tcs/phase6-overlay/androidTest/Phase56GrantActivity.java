package com.koenterprises.territorycardstudio;

/** Test-APK component: Android-only dependencies so it can run outside instrumentation. */
public final class Phase56GrantActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state) {
        super.onCreate(state);
        grantUriPermission("com.koenterprises.territorycardstudio",
            android.provider.DocumentsContract.buildDocumentUri(Phase56SyntheticDocumentsProvider.AUTHORITY,"root"),
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        grantUriPermission("com.koenterprises.territorycardstudio",
            android.provider.DocumentsContract.buildChildDocumentsUri(Phase56SyntheticDocumentsProvider.AUTHORITY,"root"),
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // Isolated synthetic test provider only: cleanup also needs stale child-document writes.
        grantUriPermission("com.koenterprises.territorycardstudio",
            android.net.Uri.parse("content://" + Phase56SyntheticDocumentsProvider.AUTHORITY + "/document"),
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
            android.content.Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        finish();
    }
}
