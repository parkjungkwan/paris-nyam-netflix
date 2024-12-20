package kr.chat.serviceImpl;


import kr.chat.document.Chat;
import kr.chat.repository.ChatRepository;
import kr.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatRepository chatRepository;

    @Override
    public Flux<Chat> mFindByChatRoomId(String chatRoomId) {
        return chatRepository.mFindByChannelId(chatRoomId)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Chat> saveMessage(Chat chat) {

        return chatRepository.save(chat);
    }


    @Override
    public Mono<Long> getUnreadMessageCountByChatRoomId(String chatRoomId, String nickname) {
        return chatRepository.findByChatRoomId(chatRoomId)
                .sort((chat1, chat2) -> chat2.getCreatedAt().compareTo(chat1.getCreatedAt())) // 최신 메시지 우선 정렬
                .flatMap(chat -> {
                    Map<String, Boolean> readBy = chat.getReadBy();
                    // readBy가 null이면 빈 맵으로 초기화
                    if (readBy == null) {
                        readBy = Map.of();
                    }
                    // 읽지 않은 메시지 체크
                    return Mono.just(!Boolean.TRUE.equals(readBy.get(nickname)));
                })
                .takeUntil(isRead -> !isRead) // 읽지 않은 메시지를 찾을 때까지 반복
                .count() // 읽지 않은 메시지 수를 세어 반환
                .map(count -> count-1L);
    }


    @Override
    public Mono<Long> getParticipantsNotReadCount(String chatId) {
        return chatRepository.findById(chatId)
                .map(chat -> {
                    long totalParticipants = chat.getTotalParticipants() != null ? chat.getTotalParticipants() : 0; // 총 참가자 수
                    long readCount = chat.getReadBy().values().stream()
                            .filter(Boolean::booleanValue) // 읽은 참가자 수
                            .count();
                    return totalParticipants - readCount; // 전체 참가자 수에서 읽은 수를 뺌
                });
    }


    @Override
    public Mono<Chat> markAsRead(String chatId, String nickname) {
        return chatRepository.findById(chatId)
                .flatMap(chat -> {
                    if (chat.getReadBy() == null) {
                        chat.setReadBy(new HashMap<>()); // readBy 초기화
                    }
                    chat.getReadBy().put(nickname, true); // 닉네임을 읽음으로 설정
                    return chatRepository.save(chat); // 업데이트된 메시지 저장
                });
    }

    //기존 메시지에 readBy값 추가
    @Override
    public Mono<Chat> updateReadBy(String chatId, String nickname) {
        return chatRepository.findById(chatId)
                .flatMap(chat -> {
                    if (chat.getReadBy() == null) {
                        chat.setReadBy(new HashMap<>()); // readBy 초기화
                    }
                    // 특정 사용자의 읽음 상태를 업데이트
                    chat.getReadBy().put(nickname, true); // 닉네임을 읽음으로 설정
                    return chatRepository.save(chat); // 업데이트된 메시지 저장
                });
    }
}