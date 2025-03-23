package com.saritha.fashions.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Document(collection = "profiles")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Profile {
    @Id
    private String id;

    private String userId;
    private String fullName;
    private String emailId;
    private String phno;
    private String dateOfBirth;
    private String gender;
    private String college;
    private String course;
    private String summary;

    private List<String> links;
    private List<SocialMedia> socialMediaAccounts;

    public Profile(String userSaved, String name, String email, String phone) {
        this.userId = userSaved;
        this.fullName = name;
        this.emailId = email;
        this.phno = phone;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SocialMedia {
        private String platform;
        private String url;

    }

    private EducationalDetails educationalDetails;
    private PaymentDetails paymentDetails;
    private List<Internships> internships;
    private List<WorkExperience> workExperience;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WorkExperience {
        private String companyName;
        private String companySector;
        private String jobTitle;
        private String jobDescription;
        private String jobLocation;
        private String workDuration;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentDetails {
        private String accountHolderName;
        private String accountNumber;
        private String reEnterAccountNumber;
        private String ifscCode;
        private String upiId;
        private String lastAddedDate;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Internships {
        private String companyName;
        private String companySector;
        private String jobTitle;
        private String jobDescription;
        private String jobLocation;
        private String internshipDuration;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EducationalDetails {
        private CurrentCourse currentCourses;
        private ClassXII classXII;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CurrentCourse {
        private String college;
        private String department;
        private String graduationYear;
        private String branch;
        private String currentSemester;

    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClassXII {
        private String college;
        private String board;
        private String percentageObtained;
        private String passingYear;

    }

    @LastModifiedDate
    private Date updatedAt;
}
