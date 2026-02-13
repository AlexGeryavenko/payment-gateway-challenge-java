package com.checkout.payment.gateway.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.client.bank.api.DefaultApi;
import com.checkout.payment.gateway.client.bank.model.BankPaymentRequest;
import com.checkout.payment.gateway.client.bank.model.BankPaymentResponse;
import com.checkout.payment.gateway.exception.BankCommunicationException;
import com.checkout.payment.gateway.model.Payment;
import com.checkout.payment.gateway.model.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

@ExtendWith(MockitoExtension.class)
class BankPaymentAdapterTest {

  @Mock
  private DefaultApi bankApi;

  @InjectMocks
  private BankPaymentAdapter bankPaymentAdapter;

  @Test
  void authorize_bankReturnsAuthorized_returnsAuthorizedStatus() {
    Payment payment = createPayment();
    when(bankApi.authorizePayment(any())).thenReturn(
        new BankPaymentResponse().authorized(true).authorizationCode("abc-123"));

    PaymentStatus result = bankPaymentAdapter.authorize(payment);

    assertThat(result).isEqualTo(PaymentStatus.AUTHORIZED);
  }

  @Test
  void authorize_bankReturnsDeclined_returnsDeclinedStatus() {
    Payment payment = createPayment();
    when(bankApi.authorizePayment(any())).thenReturn(
        new BankPaymentResponse().authorized(false).authorizationCode(""));

    PaymentStatus result = bankPaymentAdapter.authorize(payment);

    assertThat(result).isEqualTo(PaymentStatus.DECLINED);
  }

  @Test
  void authorize_bankThrowsException_throwsBankCommunicationException() {
    Payment payment = createPayment();
    when(bankApi.authorizePayment(any())).thenThrow(new RestClientException("Connection refused"));

    assertThatThrownBy(() -> bankPaymentAdapter.authorize(payment))
        .isInstanceOf(BankCommunicationException.class)
        .hasMessageContaining("Bank communication failed")
        .hasCauseInstanceOf(RestClientException.class);
  }

  @Test
  void authorize_sendsCorrectExpiryFormat() {
    Payment payment = createPayment();
    payment.setExpiryMonth(1);
    payment.setExpiryYear(2025);
    when(bankApi.authorizePayment(any())).thenReturn(
        new BankPaymentResponse().authorized(true));

    bankPaymentAdapter.authorize(payment);

    ArgumentCaptor<BankPaymentRequest> captor = ArgumentCaptor.forClass(BankPaymentRequest.class);
    verify(bankApi).authorizePayment(captor.capture());
    assertThat(captor.getValue().getExpiryDate()).isEqualTo("01/2025");
  }

  @Test
  void authorize_sendsAllFieldsToBank() {
    Payment payment = createPayment();
    when(bankApi.authorizePayment(any())).thenReturn(
        new BankPaymentResponse().authorized(true));

    bankPaymentAdapter.authorize(payment);

    ArgumentCaptor<BankPaymentRequest> captor = ArgumentCaptor.forClass(BankPaymentRequest.class);
    verify(bankApi).authorizePayment(captor.capture());
    BankPaymentRequest sent = captor.getValue();
    assertThat(sent.getCardNumber()).isEqualTo("2222405343248877");
    assertThat(sent.getExpiryDate()).isEqualTo("04/2027");
    assertThat(sent.getCurrency()).isEqualTo("GBP");
    assertThat(sent.getAmount()).isEqualTo(100);
    assertThat(sent.getCvv()).isEqualTo("123");
  }

  private Payment createPayment() {
    Payment payment = new Payment();
    payment.setCardNumber("2222405343248877");
    payment.setExpiryMonth(4);
    payment.setExpiryYear(2027);
    payment.setCurrency("GBP");
    payment.setAmount(100);
    payment.setCvv("123");
    return payment;
  }
}
