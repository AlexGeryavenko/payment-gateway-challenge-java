package com.checkout.payment.gateway.mapper;

import com.checkout.payment.gateway.api.model.PaymentDetailsResponse;
import com.checkout.payment.gateway.api.model.ProcessPaymentRequest;
import com.checkout.payment.gateway.api.model.ProcessPaymentRequest.CurrencyEnum;
import com.checkout.payment.gateway.api.model.ProcessPaymentResponse;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PaymentStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentApiMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "cardNumberLastFour", ignore = true)
  Payment toDomain(ProcessPaymentRequest request);

  ProcessPaymentResponse toProcessResponse(Payment payment);

  PaymentDetailsResponse toDetailsResponse(Payment payment);

  default String map(CurrencyEnum currency) {
    return switch (currency) {
      case GBP -> "GBP";
      case USD -> "USD";
      case EUR -> "EUR";
    };
  }

  default ProcessPaymentResponse.StatusEnum mapToProcessStatus(PaymentStatus status) {
    return switch (status) {
      case AUTHORIZED -> ProcessPaymentResponse.StatusEnum.AUTHORIZED;
      case DECLINED -> ProcessPaymentResponse.StatusEnum.DECLINED;
      case REJECTED -> ProcessPaymentResponse.StatusEnum.REJECTED;
    };
  }

  default PaymentDetailsResponse.StatusEnum mapToDetailsStatus(PaymentStatus status) {
    return switch (status) {
      case AUTHORIZED -> PaymentDetailsResponse.StatusEnum.AUTHORIZED;
      case DECLINED -> PaymentDetailsResponse.StatusEnum.DECLINED;
      case REJECTED -> throw new IllegalArgumentException(
          "REJECTED status cannot be mapped to PaymentDetailsResponse");
    };
  }
}
