package com.koenterprises.territorycardstudio;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Test-only synthetic documents. No Kotlin or test-library runtime required in provider process. */
public final class Phase56SyntheticDocumentsProvider extends DocumentsProvider {
    public static final String AUTHORITY="com.koenterprises.territorycardstudio.test.synthetic.documents";
    private File root() { File f=new File(getContext().getFilesDir(),"synthetic-documents"); if(!f.isDirectory()&&!f.mkdirs())throw new IllegalStateException("Cannot create test root");return f; }
    private File file(String id) { if(!id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Invalid test id");return new File(root(),id); }
    @Override public boolean onCreate(){return true;}
    @Override public Cursor queryRoots(String[] projection) {
        String[] cols=projection!=null?projection:new String[]{"root_id","document_id","title","flags","mime_types"};
        Map<String,Object> m=new HashMap<>();m.put("root_id","synthetic");m.put("document_id","root");m.put("title","Synthetic test files");m.put("flags",DocumentsContract.Root.FLAG_SUPPORTS_CREATE);m.put("mime_types","*/*");
        return row(cols,m);
    }
    private MatrixCursor row(String[] cols,Map<String,Object> m) { MatrixCursor c=new MatrixCursor(cols);Object[] values=new Object[cols.length];for(int i=0;i<cols.length;i++)values[i]=m.get(cols[i]);c.addRow(values);return c; }
    private MatrixCursor document(String id,String[] projection) {
        String[] cols=projection!=null?projection:new String[]{"document_id","_display_name","mime_type","flags","_size"};
        boolean isRoot=id.equals("root");File f=isRoot?root():file(id);String name;
        try{name=isRoot?"Synthetic test files":new String(Files.readAllBytes(new File(root(),id+".name").toPath()),StandardCharsets.UTF_8);}catch(IOException e){throw new IllegalStateException(e);}
        Map<String,Object> m=new HashMap<>();m.put("document_id",id);m.put("_display_name",name);m.put("mime_type",isRoot?DocumentsContract.Document.MIME_TYPE_DIR:name.endsWith(".png")?"image/png":name.endsWith(".jpg")?"image/jpeg":name.endsWith(".pdf")?"application/pdf":name.endsWith(".txt")?"text/plain":name.endsWith(".csv")?"text/csv":"application/json");m.put("flags",isRoot?DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE:DocumentsContract.Document.FLAG_SUPPORTS_WRITE|DocumentsContract.Document.FLAG_SUPPORTS_DELETE);m.put("_size",isRoot?0L:f.length());return row(cols,m);
    }
    @Override public Cursor queryDocument(String id,String[] projection){return document(id,projection);}
    @Override public Cursor queryChildDocuments(String parent,String[] projection,String sortOrder) {
        if(!parent.equals("root"))throw new IllegalArgumentException("Invalid parent");
        MatrixCursor probe=document("root",projection);String[] cols=probe.getColumnNames();probe.close();MatrixCursor result=new MatrixCursor(cols);
        File[] files=root().listFiles();if(files!=null)for(File f:files)if(f.getName().matches("[a-f0-9-]{36}"))try(Cursor c=document(f.getName(),projection)){c.moveToFirst();Object[] values=new Object[cols.length];for(int i=0;i<cols.length;i++)values[i]=c.getType(i)==Cursor.FIELD_TYPE_INTEGER?c.getLong(i):c.getString(i);result.addRow(values);}return result;
    }
    @Override public String createDocument(String parent,String mime,String name) {
        if(!parent.equals("root")||name.length()>200||name.contains("/"))throw new IllegalArgumentException("Invalid test document");String id=UUID.randomUUID().toString();
        try{Files.write(file(id).toPath(),new byte[0]);Files.write(new File(root(),id+".name").toPath(),name.getBytes(StandardCharsets.UTF_8));}catch(IOException e){throw new IllegalStateException(e);}getContext().grantUriPermission("com.koenterprises.territorycardstudio", DocumentsContract.buildDocumentUri(AUTHORITY,id), android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION);return id;
    }
    @Override public ParcelFileDescriptor openDocument(String id,String mode,CancellationSignal signal)throws java.io.FileNotFoundException{return ParcelFileDescriptor.open(file(id),ParcelFileDescriptor.parseMode(mode));}
    @Override public void deleteDocument(String id){if(!file(id).delete())throw new IllegalStateException("Cannot delete test document");new File(root(),id+".name").delete();}
}
