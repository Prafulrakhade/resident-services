package io.mosip.resident.service.impl;

import io.mosip.kernel.core.authmanager.model.AuthNResponse;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.preregistration.application.constant.PreRegLoginConstant;
import io.mosip.preregistration.core.util.GenericUtil;
import io.mosip.resident.config.LoggerConfiguration;
import io.mosip.resident.constant.ResidentErrorCode;
import io.mosip.resident.dto.*;
import io.mosip.resident.exception.ApisResourceAccessException;
import io.mosip.resident.exception.ResidentServiceCheckedException;
import io.mosip.resident.exception.ResidentServiceException;
import io.mosip.resident.service.OtpManager;
import io.mosip.resident.service.ProxyOtpService;
import io.mosip.resident.util.AuditUtil;
import io.mosip.resident.util.EventEnum;
import io.mosip.resident.validator.RequestValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author  Kamesh Shekhar Prasad
 * This class is used to implement opt service impl class.
 */
@Service
public class ProxyOtpServiceImpl implements ProxyOtpService {

    private Logger log = LoggerConfiguration.logConfig(ProxyOtpServiceImpl.class);

    private List<String> otpChannel;

    @Autowired
    private OtpManager otpManager;

    @Autowired
    private AuditUtil audit;

    @Autowired
    RequestValidator requestValidator;

    @Value("${mosip.mandatory-languages}")
    private String mandatoryLanguage;

    @Override
    public ResponseEntity<MainResponseDTO<AuthNResponse>> sendOtp(MainRequestDTO<OtpRequestDTOV2> userOtpRequest) {
        MainResponseDTO<AuthNResponse> response = new MainResponseDTO<>();
        String userid = null;
        boolean isSuccess = false;
        String language = mandatoryLanguage;
        log.info("In callsendOtp method of login service  with userID: {} and langCode",
                userOtpRequest.getRequest().getUserId(), language);

        try {
            response = (MainResponseDTO<AuthNResponse>) getMainResponseDto(userOtpRequest);
            log.info("Response after loginCommonUtil {}", response);

            userid = userOtpRequest.getRequest().getUserId();
            otpChannel = requestValidator.validateUserIdAndTransactionId(userid, userOtpRequest.getRequest().getTransactionID());
            boolean otpSent = otpManager.sendOtp(userOtpRequest, otpChannel.get(0), language);
            AuthNResponse authNResponse = null;
            if (otpSent) {
                if (otpChannel.get(0).equalsIgnoreCase(PreRegLoginConstant.PHONE_NUMBER))
                    authNResponse = new AuthNResponse(PreRegLoginConstant.SMS_SUCCESS, PreRegLoginConstant.SUCCESS);
                else
                    authNResponse = new AuthNResponse(PreRegLoginConstant.EMAIL_SUCCESS, PreRegLoginConstant.SUCCESS);
                response.setResponse(authNResponse);
                isSuccess = true;
            } else
                isSuccess = false;

            response.setResponsetime(DateUtils.getUTCCurrentDateTimeString());
        } catch (HttpServerErrorException | HttpClientErrorException ex) {
            log.error("In callsendOtp method of login service- ", ex.getResponseBodyAsString());
            audit.setAuditRequestDto(EventEnum.getEventEnumWithValue(EventEnum.SEND_OTP_FAILURE,
                    userid, "Send OTP"));
            if(ex instanceof HttpServerErrorException || ex instanceof HttpClientErrorException){
                throw new ResidentServiceException(ResidentErrorCode.CONFIG_FILE_NOT_FOUND_EXCEPTION.getErrorCode(),
                        ResidentErrorCode.CONFIG_FILE_NOT_FOUND_EXCEPTION.getErrorMessage());
            }
        }
        catch (Exception ex) {
            log.error("In callsendOtp method of login service- ", ex);
            audit.setAuditRequestDto(EventEnum.getEventEnumWithValue(EventEnum.SEND_OTP_FAILURE,
                    userid, "Send OTP"));
            throw new ResidentServiceException(ResidentErrorCode.SEND_OTP_FAILED.getErrorCode(),
                    ResidentErrorCode.SEND_OTP_FAILED.getErrorMessage());
        } finally {
            if (isSuccess) {
                audit.setAuditRequestDto(EventEnum.getEventEnumWithValue(EventEnum.SEND_OTP_SUCCESS,
                        userid, "Send OTP"));
            } else {

                ExceptionJSONInfoDTO errors = new ExceptionJSONInfoDTO(ResidentErrorCode.SEND_OTP_FAILED.getErrorCode(),
                        ResidentErrorCode.SEND_OTP_FAILED.getErrorMessage());
                List<ExceptionJSONInfoDTO> lst = new ArrayList<>();
                lst.add(errors);
                response.setErrors(lst);
                response.setResponse(null);
                audit.setAuditRequestDto(EventEnum.getEventEnumWithValue(EventEnum.SEND_OTP_FAILURE,
                        userid, "Send OTP"));
            }
        }
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @Override
    public ResponseEntity<MainResponseDTO<AuthNResponse>> validateWithUserIdOtp(MainRequestDTO<OtpRequestDTOV3> userIdOtpRequest) {
        log.info("In calluserIdOtp method of login service ");
        MainResponseDTO<AuthNResponse> response = null;
        response = (MainResponseDTO<AuthNResponse>) getMainResponseDto(userIdOtpRequest);
        String userid = null;
        boolean isSuccess = false;

        try {
            OtpRequestDTOV3 user = userIdOtpRequest.getRequest();
            userid = user.getUserId();
            boolean validated = otpManager.validateOtp(user.getOtp(), user.getUserId(), user.getTransactionID());
            AuthNResponse authresponse = new AuthNResponse();
            if (validated) {
                authresponse.setMessage(PreRegLoginConstant.VALIDATION_SUCCESS);
                authresponse.setStatus(PreRegLoginConstant.SUCCESS);

            } else {
                throw new ResidentServiceException(ResidentErrorCode.VALIDATION_UNSUCCESS.getErrorCode(),
                        ResidentErrorCode.VALIDATION_UNSUCCESS.getErrorMessage());

            }
            response.setResponse(authresponse);
            isSuccess = true;
        } catch (ResidentServiceException ex) {
            log.error("In calluserIdOtp method of login service- ", ex);
            throw new ResidentServiceException(ResidentErrorCode.VALIDATION_UNSUCCESS.getErrorCode(),
                    ResidentErrorCode.VALIDATION_UNSUCCESS.getErrorMessage());
        } catch (RuntimeException ex) {
            log.error("In calluserIdOtp method of login service- ", ex);
            throw new ResidentServiceException(ResidentErrorCode.VALIDATION_UNSUCCESS.getErrorCode(),
                    ResidentErrorCode.VALIDATION_UNSUCCESS.getErrorMessage());
        } catch (ResidentServiceCheckedException e) {
            throw new ResidentServiceException(ResidentErrorCode.VALIDATION_UNSUCCESS.getErrorCode(),
                    ResidentErrorCode.VALIDATION_UNSUCCESS.getErrorMessage());
        } catch (ApisResourceAccessException e) {
            throw new ResidentServiceException(ResidentErrorCode.API_RESOURCE_ACCESS_EXCEPTION.getErrorCode(),
                    ResidentErrorCode.API_RESOURCE_ACCESS_EXCEPTION.getErrorMessage());
        } finally {
            response.setResponsetime(GenericUtil.getCurrentResponseTime());

            if (isSuccess) {
                audit.setAuditRequestDto(EventEnum.getEventEnumWithValue(EventEnum.VALIDATE_OTP_SUCCESS,
                        userid, "Validate OTP Success"));
            } else {
                ExceptionJSONInfoDTO errors = new ExceptionJSONInfoDTO(ResidentErrorCode.OTP_VALIDATION_FAILED.getErrorCode(),
                        ResidentErrorCode.OTP_VALIDATION_FAILED.getErrorMessage());
                List<ExceptionJSONInfoDTO> lst = new ArrayList<>();
                lst.add(errors);
                response.setErrors(lst);
                response.setResponse(null);
                audit.setAuditRequestDto(EventEnum.getEventEnumWithValue(EventEnum.OTP_VALIDATION_FAILED,
                        userid, "Validate OTP Failed"));
            }

        }
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * This method will return the MainResponseDTO with id and version
     *
     * @param mainRequestDto
     * @return MainResponseDTO<?>
     */
    public MainResponseDTO<?> getMainResponseDto(MainRequestDTO<?> mainRequestDto) {
        log.info("In getMainResponseDTO method of ProxyOtpServiceImpl");
        MainResponseDTO<?> response = new MainResponseDTO<>();
        response.setId(mainRequestDto.getId());
        response.setVersion(mainRequestDto.getVersion());
        return response;
    }

}