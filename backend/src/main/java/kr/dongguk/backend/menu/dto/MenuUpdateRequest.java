package kr.dongguk.backend.menu.dto;

/** 둘 중 보낸 것만 바꿈. groupName: 옮길 메뉴군 이름(없으면 새로 만듦), discontinued: 단종 여부 */
public record MenuUpdateRequest(String groupName, Boolean discontinued) {}