package com.labsem.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.labsem.common.exception.BusinessException;
import com.labsem.entity.Member;
import com.labsem.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberMapper memberMapper;

    /**
     * List all members ordered by name ASC.
     */
    public List<Member> list() {
        return memberMapper.selectList(
                new LambdaQueryWrapper<Member>().orderByAsc(Member::getName)
        );
    }

    /**
     * Create a member. Lowercase email, check unique.
     */
    public Member create(String name, String email) {
        String normalizedEmail = email.trim().toLowerCase();
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<Member>().eq(Member::getEmail, normalizedEmail)
        );
        if (count > 0) {
            throw new BusinessException(400, "该邮箱已存在");
        }
        Member member = new Member();
        member.setName(name.trim());
        member.setEmail(normalizedEmail);
        memberMapper.insert(member);
        return member;
    }

    /**
     * Update a member. Check email unique excluding self.
     */
    public Member update(Long memberId, String name, String email) {
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException.notFound("成员不存在");
        }
        String normalizedEmail = email.trim().toLowerCase();
        Long count = memberMapper.selectCount(
                new LambdaQueryWrapper<Member>()
                        .eq(Member::getEmail, normalizedEmail)
                        .ne(Member::getId, memberId)
        );
        if (count > 0) {
            throw new BusinessException(400, "该邮箱已被其他成员使用");
        }
        member.setName(name.trim());
        member.setEmail(normalizedEmail);
        memberMapper.updateById(member);
        return member;
    }

    /**
     * Delete a member by id.
     */
    public void delete(Long memberId) {
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new BusinessException.notFound("成员不存在");
        }
        memberMapper.deleteById(memberId);
    }
}
