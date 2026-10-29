package co.edu.uniquindio.AgendasDT.dto;

import java.util.List;
import co.edu.uniquindio.AgendasDT.domain.Meeting;
import org.springframework.data.domain.Page;

public record MeetingListResponse(List<MeetingResponse> content, int page, int size,
                                  long totalElements, int totalPages) {
    public static MeetingListResponse from(Page<Meeting> meetings) {
        return new MeetingListResponse(meetings.getContent().stream().map(MeetingResponse::from).toList(),
                meetings.getNumber(), meetings.getSize(), meetings.getTotalElements(), meetings.getTotalPages());
    }
}
