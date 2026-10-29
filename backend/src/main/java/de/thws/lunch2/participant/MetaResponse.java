package de.thws.lunch2.participant;

import de.thws.lunch2.common.CatalogService.CampusOption;
import de.thws.lunch2.common.CatalogService.InterestOption;
import de.thws.lunch2.common.CatalogService.LanguageOption;
import de.thws.lunch2.common.CatalogService.StudyFieldOption;
import de.thws.lunch2.common.SlotService.Slot;

import java.util.List;

public record MetaResponse(List<CampusOption> campuses, List<LanguageOption> languages,
                           List<InterestOption> interests, List<StudyFieldOption> studyFields, List<Slot> slots,
                           List<de.thws.lunch2.common.MensaService.Mensa> mensas) {}
