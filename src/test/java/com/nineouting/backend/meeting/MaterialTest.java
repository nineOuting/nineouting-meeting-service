package com.nineouting.backend.meeting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nineouting.backend.meeting.repository.MeetingMaterialRepository;
import com.nineouting.backend.meeting.service.MaterialStorage;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** REQ-067 회의 사전 자료: 실제 LocalStack S3에 올리고 받는다 */
class MaterialTest extends ApiTestSupport {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    MaterialStorage materialStorage;

    @Autowired
    MeetingMaterialRepository materialRepository;

    @Test
    void 참석자가_올린_자료를_회의를_볼_수_있는_사람이_내려받고_올린_사람이_지운다() throws Exception {
        Long workspaceId = newId();
        Long owner = newId();
        Long attendee = newId();
        Long viewer = newId();
        Long folderId = folder(workspaceId, "폴더", owner, attendee, viewer);
        Long meetingId = createMeeting(folderId, owner, owner, attendee);
        String path = "/api/meetings/" + meetingId + "/materials";
        byte[] content = "회의 자료 내용".getBytes(StandardCharsets.UTF_8);

        // 참석자가 아니면 올릴 수 없다
        call(viewer, post(path), uploadJson("안건 정리.txt", content.length)).andExpect(status().isForbidden());
        var prepared = call(attendee, post(path), uploadJson("안건 정리.txt", content.length))
                .andExpect(status().isCreated());
        Long materialId = id(prepared, "$.data.materialId");
        String uploadUrl = data(prepared, "$.data.uploadUrl");

        // 올리기 전에 다 올렸다고 하면 거절하고, 목록에도 없다
        call(attendee, post(path + "/" + materialId + "/complete"), null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATERIAL_NOT_UPLOADED"));
        call(viewer, get(path), null).andExpect(jsonPath("$.data.length()").value(0));

        HttpResponse<Void> put = httpClient.send(HttpRequest.newBuilder(URI.create(uploadUrl))
                .header("Content-Type", "text/plain")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content)).build(), HttpResponse.BodyHandlers.discarding());
        assertThat(put.statusCode()).isEqualTo(200);
        call(attendee, post(path + "/" + materialId + "/complete"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(content.length));

        var listed = call(viewer, get(path), null)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fileName").value("안건 정리.txt"))
                .andExpect(jsonPath("$.data[0].uploadedBy.userId").value(attendee));
        String downloadUrl = data(listed, "$.data[0].downloadUrl");
        HttpResponse<byte[]> downloaded = httpClient.send(HttpRequest.newBuilder(URI.create(downloadUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(downloaded.body()).isEqualTo(content);

        // 올린 사람도, 회의를 만든 사람도 아니면 지울 수 없다
        call(viewer, delete(path + "/" + materialId), null).andExpect(status().isForbidden());
        String key = materialRepository.findById(materialId).orElseThrow().getStorageKey();
        call(attendee, delete(path + "/" + materialId), null).andExpect(status().isOk());
        call(viewer, get(path), null).andExpect(jsonPath("$.data.length()").value(0));
        assertThat(materialStorage.size(key)).isEmpty();
    }

    @Test
    void 오십MB를_넘는_파일은_업로드_주소를_주지_않는다() throws Exception {
        Long workspaceId = newId();
        Long owner = newId();
        Long folderId = folder(workspaceId, "폴더", owner);
        Long meetingId = createMeeting(folderId, owner, owner);

        call(owner, post("/api/meetings/" + meetingId + "/materials"), uploadJson("큰 파일.mp4", 50L * 1024 * 1024 + 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MATERIAL_TOO_LARGE"));
    }

    @Test
    void 취소된_회의에는_자료를_올리지_않는다() throws Exception {
        Long workspaceId = newId();
        Long owner = newId();
        Long folderId = folder(workspaceId, "폴더", owner);
        Long meetingId = createMeeting(folderId, owner, owner);
        call(owner, post("/api/meetings/" + meetingId + "/cancel"), null).andExpect(status().isOk());

        call(owner, post("/api/meetings/" + meetingId + "/materials"), uploadJson("자료.txt", 10))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEETING_CANCELLED"));
    }

    @Test
    void 회의를_영구_삭제하면_사전_자료_파일도_지운다() throws Exception {
        Long workspaceId = newId();
        Long owner = newId();
        Long folderId = folder(workspaceId, "폴더", owner);
        Long meetingId = createMeeting(folderId, owner, owner);
        String path = "/api/meetings/" + meetingId + "/materials";
        byte[] content = "자료".getBytes(StandardCharsets.UTF_8);
        var prepared = call(owner, post(path), uploadJson("자료.txt", content.length)).andExpect(status().isCreated());
        Long materialId = id(prepared, "$.data.materialId");
        httpClient.send(HttpRequest.newBuilder(URI.create(data(prepared, "$.data.uploadUrl")))
                .header("Content-Type", "text/plain")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content)).build(), HttpResponse.BodyHandlers.discarding());
        call(owner, post(path + "/" + materialId + "/complete"), null).andExpect(status().isOk());
        String key = materialRepository.findById(materialId).orElseThrow().getStorageKey();

        call(owner, delete("/api/meetings/" + meetingId), null).andExpect(status().isOk());
        call(owner, delete("/api/meetings/" + meetingId + "/purge"), null).andExpect(status().isOk());

        assertThat(materialStorage.size(key)).isEmpty();
        assertThat(materialRepository.findById(materialId)).isEmpty();
    }

    private static String uploadJson(String fileName, long size) {
        return "{\"fileName\":\"%s\",\"contentType\":\"text/plain\",\"size\":%d}".formatted(fileName, size);
    }
}
