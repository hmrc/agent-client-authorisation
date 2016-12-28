/*
 * Copyright 2016 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentclientauthorisation

import uk.gov.hmrc.play.test.UnitSpec

class GenerateApplicationIdWhitelistingSpec  extends UnitSpec {


  "Application IDS" should {

    "be generated" ignore {

      List(
        "ab349380-17cc-4de0-a7ac-c76baedd7133",
        "53f6fcb7-67b4-4801-8727-b8b2521e2904",
        "6dfac0f3-370b-4306-8f51-d82e5c276faa",
        "fa9ed720-f0e1-4268-8287-e23e03ae11cd",
        "b1860fa5-3eb9-452d-9369-ed195fab7c98",
        "2f798389-1529-4cb7-a397-ddc482d15c07",
        "122bbafa-b462-4b47-b1b9-a73789a59083",
        "0295fa00-892d-4183-a290-974bf1c6b340",
        "qCzehf9axC21LUyg5wndIj6eq5ga",
        "a6f2205b-fc83-4013-a7fc-b9dbc68cbadf",
        "b51778e9-fd86-4d2a-a122-41831daecb2c",
        "154938be-17ca-4041-86c6-5e7cbffa87b7",
        "7538d34b-6c06-4993-8eb4-00f0558ea917",
        "2023c7e5-19fa-4641-a96b-17ac79576376",
        "bd76aa86-1166-4af4-8e94-13db5bc0a149",
        "bc252f64-442d-41c0-aa21-49e24ea42ce4",
        "6c80af39-4a1b-4561-8538-ef350d8c065a",
        "f504bf1f-4c7b-4c39-930d-42011bb7ae5f",
        "/b9f5af26-4672-41df-afd1-a8a9541118bf",
        "78350730-7a6b-4b15-b75d-519afaf9d782",
        "eeeb6583-49c1-4c26-aa74-a8b720c811fb",
        "a83b18ce-7913-4434-b5e8-5eb377e00b27",
        "453c2615-2822-4463-a63c-7e9f902a73ff",
        "583d87fd-89ff-40b5-b04e-48b40e7cabc3",
        "59056e18-cbfb-48bb-83c7-139b5bff7e36",
        "ead68259-9f2f-47f9-94e7-e808799af76a",
        "75754eb9-b1e7-4c72-9dc1-7122225ce4a1",
        "f8bd7617-0ef6-406d-9160-f829e76ba6d3",
        "8bbb408d-8d28-4680-b80d-7a603df39628",
        "204472d9-d954-44a9-b0fc-acd120fea8c4",
        "d72b0fcf-f9a0-4786-ba6f-f60ff0caeaf9",
        "33575d00-95cb-4e36-97b0-949d99b0a081",
        "7b0a1b25-3c9f-4d05-9004-0cb64d43eb72",
        "4ac57c01-8341-4c43-bb66-ca1c1419bc24",
        "e636285f-5433-4f94-9b86-c13afd5c781a",
        "4932e8f6-c080-45cd-a487-e581be51d81e",
        "428329ab-17d3-417f-b0eb-f29ebb89f2b0",
        "d2b1e2fc-d987-486a-84f7-6f7bf6e29557",
        "VoEDdC9D2FI37Tw3w5A_6WKFMAYa",
        "26250dbf-f670-4234-bf45-884701aee2b4",
        "kwGpcfaDGonctXJqcZjFTYsVpsQa",
        "fEjCyBRdl0q_EF7faGxPFlHZyKga",
        "hLgzXuE8mTqhI3uvAHfS_N1Ld6ga",
        "l6zZWFIx1RZVhK__xSn1xXsTUQoa",
        "7Oyb8p_mSob2e2z6yCoWVNO4gXsa",
        "2940ba1a-aa3a-402c-95b8-205bc748c9b3",
        "72559cd2-b025-4670-b38f-a70792a261c0",
        "21ce7539-802b-43d8-9220-d0e08b5f2f6b",
        "b6c441b4-048a-411c-b5e7-974506844ddc",
        "FM2WfZKAzMV04KAxq1zmhiVUXJ4a",
        "5bfb8708-0300-4a17-bd5d-38605419154e",
        "e1c04b90-71cf-478b-b1fc-3d57821e5462",
        "6673cd36-f6e9-4e99-b055-3e8abb425a15",
        "8b33daa8-05c9-430f-8940-fa08c769c0ec",
        "7193670c-f7a3-467d-aac1-b6231beb87a1",
        "b7e9dc84-5659-4fcf-ac7b-d07e4a7780bf",
        "884afc35-5e5b-45d2-b7af-670ad902405d",
        "db4def70-3ae9-4c19-a1b2-70f02d0bd830",
        "435a51d8-d121-4c99-ad36-af1d8e4d92a9").zipWithIndex.foreach{
        case (id, index) =>

          println(s"""api.access.white-list.applicationIds.$index: "$id"""")
      }
    }
  }
}
