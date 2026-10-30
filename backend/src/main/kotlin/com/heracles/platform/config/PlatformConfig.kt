package com.heracles.platform.config

import com.heracles.platform.application.GetMe
import com.heracles.platform.application.ProvisionUser
import com.heracles.platform.application.port.out.TransactionContext
import com.heracles.platform.application.port.out.UserRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Use cases are plain classes (no framework in `application`); this is where they are wired. */
@Configuration
class PlatformConfig {
    @Bean
    fun provisionUser(transactions: TransactionContext, users: UserRepository) = ProvisionUser(transactions, users)

    @Bean
    fun getMe(transactions: TransactionContext, users: UserRepository) = GetMe(transactions, users)
}
