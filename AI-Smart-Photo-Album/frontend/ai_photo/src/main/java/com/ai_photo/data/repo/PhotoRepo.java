package com.ai_photo.data.repo;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import com.ai_photo.data.api.RetrofitClient;
import com.ai_photo.data.model.photo.*;
import com.ai_photo.util.Result;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class PhotoRepo {

    public Result<PhotoUploadResponse> uploadFromUris(Context ctx, List<Uri> uris) {
        ContentResolver cr = ctx.getContentResolver();
        List<MultipartBody.Part> parts = new ArrayList<>();
        for (Uri uri : uris) {
            try {
                String mime = cr.getType(uri);
                if (mime == null) mime = "image/jpeg";
                MediaType mt = MediaType.parse(mime);
                byte[] bytes = readAll(cr.openInputStream(uri));
                String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                if (ext == null) ext = "jpg";
                String filename = "upload_" + System.currentTimeMillis() + "." + ext;
                RequestBody rb = RequestBody.create(mt, bytes);
                parts.add(MultipartBody.Part.createFormData("files", filename, rb));
            } catch (Exception e) {
                return Result.net(e);
            }
        }
        return RetrofitClient.exec(RetrofitClient.api().uploadPhotos(parts));
    }

    public Result<PhotoListResponse> list(int page, int pageSize) {
        return RetrofitClient.exec(RetrofitClient.api().listPhotos(page, pageSize));
    }

    public Result<PhotoDetailResponse> detail(long id) {
        return RetrofitClient.exec(RetrofitClient.api().photoDetail(id));
    }

    public Result<Object> update(long id, List<String> tags, String description) {
        PhotoUpdateRequest req = new PhotoUpdateRequest();
        req.tags = tags; req.description = description;
        return RetrofitClient.execVoid(RetrofitClient.api().updatePhoto(id, req));
    }

    public Result<Object> favorite(long id) {
        return RetrofitClient.execVoid(RetrofitClient.api().favorite(id));
    }

    public Result<Object> unfavorite(long id) {
        return RetrofitClient.execVoid(RetrofitClient.api().unfavorite(id));
    }

    public Result<SearchResponse> search(String query, int page, int pageSize) {
        SearchRequest req = new SearchRequest();
        req.query = query; req.page = page; req.pageSize = pageSize;
        return RetrofitClient.exec(RetrofitClient.api().search(req));
    }

    public Result<SearchResponse> filter(Long sceneId, Long emotionId, Long tagId, int page, int pageSize) {
        FilterRequest req = new FilterRequest();
        req.sceneId = sceneId; req.emotionId = emotionId; req.tagId = tagId;
        req.page = page; req.pageSize = pageSize;
        return RetrofitClient.exec(RetrofitClient.api().filter(req));
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        return out.toByteArray();
    }
}
