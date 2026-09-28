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
        String[] revoke = getIntent().getStringArrayExtra("revokeDocuments");
        if (revoke != null) for (String uri : revoke) revokeUriPermission(android.net.Uri.parse(uri),
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (getIntent().getBooleanExtra("cleanup", false)) {
            android.net.Uri children = android.provider.DocumentsContract.buildChildDocumentsUri(Phase56SyntheticDocumentsProvider.AUTHORITY,"root");
            try (android.database.Cursor rows = getContentResolver().query(children,new String[]{"document_id"},null,null,null)) {
                while (rows != null && rows.moveToNext()) android.provider.DocumentsContract.deleteDocument(getContentResolver(),
                    android.provider.DocumentsContract.buildDocumentUri(Phase56SyntheticDocumentsProvider.AUTHORITY,rows.getString(0)));
            } catch (java.io.FileNotFoundException e) { throw new IllegalStateException(e); }
        }
        Phase56SyntheticDocumentsProvider.setupNonce = getIntent().getStringExtra("setupNonce");
        finish();
    }
}
