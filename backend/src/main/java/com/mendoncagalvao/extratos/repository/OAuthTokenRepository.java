package com.mendoncagalvao.extratos.repository;

import com.mendoncagalvao.extratos.domain.OAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthTokenRepository extends JpaRepository<OAuthToken, String> {
}
