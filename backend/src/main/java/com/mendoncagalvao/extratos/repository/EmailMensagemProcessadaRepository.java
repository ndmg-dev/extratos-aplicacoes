package com.mendoncagalvao.extratos.repository;

import com.mendoncagalvao.extratos.domain.EmailMensagemProcessada;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailMensagemProcessadaRepository extends JpaRepository<EmailMensagemProcessada, String> {
}
