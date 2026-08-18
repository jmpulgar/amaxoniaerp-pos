package com.amaxoniaerp.features.auth.domain

import com.amaxoniaerp.core.error.ApiException
import com.amaxoniaerp.core.error.ErrorCategory

open class AuthenticationException(
    message: String,
) : ApiException(ErrorCategory.Unauthorized, message)

open class AuthorizationException(
    message: String,
) : ApiException(ErrorCategory.Forbidden, message)

open class NotFoundException(
    message: String,
) : ApiException(ErrorCategory.NotFound, message)
