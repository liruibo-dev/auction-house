package com.auctionhouse.exception;

import com.auctionhouse.dto.ApiResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateUsernameException.class)
    public ApiResponse<Void> handleDuplicateUsername(DuplicateUsernameException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(InvalidRegisterException.class)
    public ApiResponse<Void> handleInvalidRegister(InvalidRegisterException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(InvalidLoginException.class)
    public ApiResponse<Void> handleInvalidLogin(InvalidLoginException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(ItemNotFoundException.class)
    public ApiResponse<Void> handleItemNotFound(ItemNotFoundException e) {
        return ApiResponse.error(404, e.getMessage());
    }

    @ExceptionHandler(InvalidItemException.class)
    public ApiResponse<Void> handleInvalidItem(InvalidItemException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(AuctionEndedException.class)
    public ApiResponse<Void> handleAuctionEnded(AuctionEndedException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(InvalidBidException.class)
    public ApiResponse<Void> handleInvalidBid(InvalidBidException e) {
        return ApiResponse.error(400, e.getMessage());
    }

    @ExceptionHandler(InsufficientBalanceException.class)
    public ApiResponse<Void> handleInsufficientBalance(InsufficientBalanceException e) {
        return ApiResponse.error(400, e.getMessage());
    }
}
