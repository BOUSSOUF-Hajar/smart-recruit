package com.smartrecruit.referential.web;

import com.smartrecruit.referential.application.SkillService;
import com.smartrecruit.referential.application.SkillView;
import com.smartrecruit.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** {@code docs/api.md} §3 : autocomplétion (tout utilisateur connecté) et « obtenir ou créer » (C, R). */
@RestController
@RequestMapping("/api/v1/skills")
class SkillController {

    private static final int MAX_QUERY_LENGTH = 80;

    private final SkillService skills;

    SkillController(SkillService skills) {
        this.skills = skills;
    }

    @GetMapping
    List<SkillView> search(@RequestParam(defaultValue = "") String q,
                           @RequestParam(defaultValue = "20") int size) {
        String query = q.length() > MAX_QUERY_LENGTH ? q.substring(0, MAX_QUERY_LENGTH) : q;
        return skills.search(query, size);
    }

    /** 201 si la compétence est créée, 200 si elle existait déjà sous ce nom ou un alias. */
    @PostMapping
    ResponseEntity<SkillView> getOrCreate(@Valid @RequestBody CreateSkillRequest request, CurrentUser user) {
        SkillService.GetOrCreateResult result = skills.getOrCreate(request.name(), user.id());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.skill());
    }
}
